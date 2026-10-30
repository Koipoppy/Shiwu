package com.shiwu.scan3d.recon

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.shiwu.scan3d.data.ApiException
import com.shiwu.scan3d.ui.components.SubjectSegmenter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.FloatBuffer

/**
 * 端上离线出模：TripoSR 拆成两张 ONNX 图跑在手机上，全程不联网。
 *
 *   encoder.onnx  1 张图 (1,3,512,512) → 三平面 (1,3,40,64,64)，整单只跑一次
 *   query.onnx    三平面 + N 个点 → 密度/颜色，网格点有上百万个，分块喂
 *
 * 权重是 fp16（799 MB），输入输出走 fp32，省得在 Kotlin 里处理 half 缓冲。
 * 抽面用 [SurfaceNets]（现成的），所以不依赖 torchmcubes。
 */
object TripoSrLocal {
    private const val TAG = "TripoSrLocal"

    private const val INPUT_SIZE = 512
    /** 底片解码后的最长边，抠图在这上面做就够，不必吃满原图。 */
    private const val MAX_SIDE = 1024
    /** 主体在 512 画布上占的边长（对应官方 foreground_ratio 0.85）。 */
    private const val TARGET_SIDE = INPUT_SIZE * 0.85f
    /** 官方渲染半径，和 config.yaml 的 renderer.radius 一致。 */
    private const val RADIUS = 0.87f
    /** config.yaml 的 density_bias = -1；抽面阈值官方脚本用 25。 */
    private const val ISO = 25f
    /**
     * 网格分辨率，官方默认就是 256。
     * 实测 128³ 分辨不出这个模型的等值面（会塌成两团），别往下调。
     * 256³ = 1680 万点，手机上查询大约 35-40 秒。
     */
    private const val GRID = 256
    private const val CHUNK = 32768
    private const val SMOOTHING_PASSES = 2

    fun dir(context: Context): File = File(context.filesDir, "triposr")

    fun encoderFile(context: Context): File = File(dir(context), "encoder.onnx")

    fun queryFile(context: Context): File = File(dir(context), "query.onnx")

    /** 两张图都在才认为端上引擎可用。 */
    fun isReady(context: Context): Boolean =
        encoderFile(context).let { it.exists() && it.length() > 1_000_000 } &&
                queryFile(context).let { it.exists() && it.length() > 10_000 }

    @Volatile
    private var sessions: Pair<OrtSession, OrtSession>? = null

    private fun sessions(context: Context): Pair<OrtSession, OrtSession> {
        sessions?.let { return it }
        synchronized(this) {
            sessions?.let { return it }
            val env = OrtEnvironment.getEnvironment()
            val options = OrtSession.SessionOptions().apply {
                // 手机上给满核；ORT 会自己做算子级并行
                setIntraOpNumThreads(Runtime.getRuntime().availableProcessors().coerceIn(2, 6))
            }
            val started = System.currentTimeMillis()
            val encoder = env.createSession(encoderFile(context).absolutePath, options)
            val query = env.createSession(queryFile(context).absolutePath, options)
            Log.i(TAG, "sessions ready in ${System.currentTimeMillis() - started}ms")
            return (encoder to query).also { sessions = it }
        }
    }

    fun close() {
        runCatching {
            sessions?.first?.close()
            sessions?.second?.close()
        }
        sessions = null
    }

    suspend fun reconstruct(
        context: Context,
        imagePath: String,
        segmenter: SubjectSegmenter?,
        seed: Pair<Float, Float>,
        target: File,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.Default) {
        if (!isReady(context)) throw ApiException("端上模型未安装")
        val (encoder, query) = sessions(context)
        val env = OrtEnvironment.getEnvironment()

        // 1) 抠主体 + 居中铺灰底 + 缩到 512。TripoSR 的训练数据就是这样构图的，
        //    直接喂原始照片它会把你整张桌子的场景当成一个物体去"重建"。
        onProgress(0.05f, "抠出主体")
        val input = prepareInput(imagePath, segmenter, seed)

        // 2) 编码出三平面
        onProgress(0.12f, "编码图像")
        val t0 = System.currentTimeMillis()
        val triplane: FloatArray = OnnxTensor.createTensor(
            env, FloatBuffer.wrap(input), longArrayOf(1, 3, INPUT_SIZE.toLong(), INPUT_SIZE.toLong())
        ).use { tensor ->
            encoder.run(mapOf("image" to tensor)).use { result ->
                val value = result.get(0) as OnnxTensor
                val buffer = value.floatBuffer
                FloatArray(buffer.remaining()).also { buffer.get(it) }
            }
        }
        Log.i(TAG, "encoder ${System.currentTimeMillis() - t0}ms")
        var mn = Float.MAX_VALUE
        var mx = -Float.MAX_VALUE
        var sum = 0.0
        var finite = 0
        for (v in triplane) {
            if (v.isNaN() || v.isInfinite()) continue
            finite++
            if (v < mn) mn = v
            if (v > mx) mx = v
            sum += v
        }
        Log.i(
            TAG,
            "triplane finite=$finite/${triplane.size} min=$mn max=$mx mean=${sum / finite} " +
                    "head=${triplane.take(4)}"
        )

        // 3) 密度场：GRID³ 个点分块查
        onProgress(0.3f, "生成密度场")
        val planeShape = longArrayOf(3, 40, 64, 64)
        val planeTensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(triplane), planeShape)
        val total = GRID * GRID * GRID
        val density = FloatArray(total)
        val positions = FloatArray(CHUNK * 3)
        try {
            var index = 0
            while (index < total) {
                ensureActive()
                val count = minOf(CHUNK, total - index)
                fillPositions(positions, index, count)
                OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(positions, 0, count * 3), longArrayOf(count.toLong(), 3)
                ).use { posTensor ->
                    query.run(mapOf("triplane" to planeTensor, "positions" to posTensor)).use { out ->
                        val densityTensor = out.get(0) as OnnxTensor
                        val buffer = densityTensor.floatBuffer
                        buffer.get(density, index, count)
                    }
                }
                index += count
                val ratio = index.toFloat() / total
                onProgress(0.3f + 0.42f * ratio, "生成密度场 ${(ratio * 100).toInt()}%")
            }
        } finally {
            planeTensor.close()
        }
        val t1 = System.currentTimeMillis()
        var dmin = Float.MAX_VALUE
        var dmax = -Float.MAX_VALUE
        var dsum = 0.0
        var above = 0
        for (d in density) {
            if (d < dmin) dmin = d
            if (d > dmax) dmax = d
            dsum += d
            if (d > ISO) above++
        }
        Log.i(
            TAG,
            "density $GRID^3 ${t1 - t0}ms min=$dmin max=$dmax mean=${dsum / density.size} " +
                    "aboveIso=$above (${above * 100f / density.size}%) head=${density.take(4)}"
        )

        // 4) 抽面
        onProgress(0.74f, "抽取网格")
        val mesh = SurfaceNets.build(density, GRID, GRID, GRID, SMOOTHING_PASSES, ISO)
        if (mesh.indices.isEmpty()) throw ApiException("模型生成失败，请换一张底片")
        val scale = 2f * RADIUS / (GRID - 1)
        var i = 0
        while (i < mesh.positions.size) {
            mesh.positions[i] = mesh.positions[i] * scale - RADIUS
            mesh.positions[i + 1] = mesh.positions[i + 1] * scale - RADIUS
            mesh.positions[i + 2] = mesh.positions[i + 2] * scale - RADIUS
            i += 3
        }
        Log.i(TAG, "mesh verts=${mesh.positions.size / 3} tris=${mesh.indices.size / 3}")

        // 5) 顶点色：对网格顶点再查一次
        onProgress(0.86f, "上色")
        val colors = queryColors(env, query, planeTensorFor(env, triplane), mesh.positions)

        // 6) 导出
        onProgress(0.94f, "导出 GLB")
        if (!GlbWriter.write(target, mesh.positions, mesh.normals, colors, mesh.indices)) {
            throw ApiException("模型导出失败")
        }
        onProgress(1f, "完成")
        target
    }

    private fun planeTensorFor(env: OrtEnvironment, triplane: FloatArray): OnnxTensor =
        OnnxTensor.createTensor(env, FloatBuffer.wrap(triplane), longArrayOf(3, 40, 64, 64))

    private fun queryColors(
        env: OrtEnvironment,
        query: OrtSession,
        plane: OnnxTensor,
        positions: FloatArray
    ): FloatArray {
        val vertexCount = positions.size / 3
        val colors = FloatArray(vertexCount * 3) { 0.6f }
        val chunk = 16384
        try {
            var index = 0
            while (index < vertexCount) {
                val count = minOf(chunk, vertexCount - index)
                val pts = FloatArray(count * 3)
                System.arraycopy(positions, index * 3, pts, 0, count * 3)
                OnnxTensor.createTensor(
                    env, FloatBuffer.wrap(pts), longArrayOf(count.toLong(), 3)
                ).use { posTensor ->
                    query.run(mapOf("triplane" to plane, "positions" to posTensor)).use { out ->
                        val colorTensor = out.get(1) as OnnxTensor
                        colorTensor.floatBuffer.get(colors, index * 3, count * 3)
                    }
                }
                index += count
            }
        } finally {
            plane.close()
        }
        return colors
    }

    /** 把第 index..index+count 个网格点写进 positions（xyz 顺序与 density 的下标序一致）。 */
    private fun fillPositions(positions: FloatArray, index: Int, count: Int) {
        val plane = GRID * GRID
        val step = 2f * RADIUS / (GRID - 1)
        for (k in 0 until count) {
            val linear = index + k
            val x = linear % GRID
            val y = (linear / GRID) % GRID
            val z = linear / plane
            positions[k * 3] = -RADIUS + x * step
            positions[k * 3 + 1] = -RADIUS + y * step
            positions[k * 3 + 2] = -RADIUS + z * step
        }
    }

    /**
     * 底片 → 512×512 的 0..1 RGB 平面数据（CHW）。
     *
     * 和官方 preprocess 对齐：抠掉背景 → 按主体外接框裁成正方形 → 主体缩到画面 85%
     * → 背景铺 0.5 灰。少了这一步，模型会把背景也当成物体的一部分。
     */
    private fun prepareInput(
        path: String,
        segmenter: SubjectSegmenter?,
        seed: Pair<Float, Float>
    ): FloatArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0) throw ApiException("底片读取失败")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= MAX_SIDE && bounds.outHeight / (sample * 2) >= MAX_SIDE) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw ApiException("底片读取失败")

        val mask = segmenter?.segmentMask(decoded, seed.first, seed.second)
        val composed = composeOnGray(decoded, mask)
        if (mask != null) mask.recycle()
        decoded.recycle()

        val scaled = Bitmap.createScaledBitmap(composed, INPUT_SIZE, INPUT_SIZE, true)
        if (scaled !== composed) composed.recycle()
        val pixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        scaled.recycle()

        val out = FloatArray(3 * INPUT_SIZE * INPUT_SIZE)
        val plane = INPUT_SIZE * INPUT_SIZE
        for (i in pixels.indices) {
            val px = pixels[i]
            out[i] = ((px shr 16) and 0xFF) / 255f
            out[plane + i] = ((px shr 8) and 0xFF) / 255f
            out[plane * 2 + i] = (px and 0xFF) / 255f
        }
        return out
    }

    /** 按 mask 裁主体、居中放大到 85%、背景铺灰；没有 mask 时退化成中央裁切。 */
    private fun composeOnGray(source: Bitmap, mask: Bitmap?): Bitmap {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)

        var left = w
        var top = h
        var right = -1
        var bottom = -1
        val alpha = ByteArray(w * h)
        if (mask != null) {
            val maskPixels = IntArray(mask.width * mask.height)
            val scaledMask = if (mask.width == w && mask.height == h) mask
            else Bitmap.createScaledBitmap(mask, w, h, true)
            scaledMask.getPixels(maskPixels, 0, w, 0, 0, w, h)
            if (scaledMask !== mask) scaledMask.recycle()
            for (i in maskPixels.indices) {
                // MagicTouch 的输出是「主体不透明白、背景全透明」
                val opaque = (maskPixels[i] ushr 24) and 0xFF
                if (opaque > 127) {
                    alpha[i] = 1
                    val x = i % w
                    val y = i / w
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) {
            // 没有可用 mask：取中央 70% 当成主体，至少保证不被背景撑满
            left = (w * 0.15f).toInt(); right = (w * 0.85f).toInt()
            top = (h * 0.10f).toInt(); bottom = (h * 0.90f).toInt()
            java.util.Arrays.fill(alpha, 1)
        }

        val out = Bitmap.createBitmap(INPUT_SIZE, INPUT_SIZE, Bitmap.Config.ARGB_8888)
        val outPixels = IntArray(INPUT_SIZE * INPUT_SIZE)
        java.util.Arrays.fill(outPixels, 0xFF808080.toInt())   // 背景灰 = 0.5

        // 主体占画面 85%：目标框边长 = 512 * 0.85
        val boxW = (right - left + 1).toFloat()
        val boxH = (bottom - top + 1).toFloat()
        val scale = TARGET_SIDE / maxOf(boxW, boxH)
        val destW = (boxW * scale).toInt().coerceAtLeast(1)
        val destH = (boxH * scale).toInt().coerceAtLeast(1)
        val offsetX = (INPUT_SIZE - destW) / 2
        val offsetY = (INPUT_SIZE - destH) / 2

        for (y in 0 until destH) {
            val sy = top + (y / scale).toInt().coerceIn(0, h - 1)
            val dy = y + offsetY
            if (dy < 0 || dy >= INPUT_SIZE) continue
            for (x in 0 until destW) {
                val sx = left + (x / scale).toInt().coerceIn(0, w - 1)
                val si = sy * w + sx
                if (alpha[si].toInt() == 0) continue
                val dx = x + offsetX
                if (dx < 0 || dx >= INPUT_SIZE) continue
                outPixels[dy * INPUT_SIZE + dx] = pixels[si] or (0xFF shl 24)
            }
        }
        out.setPixels(outPixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE)
        return out
    }
}
