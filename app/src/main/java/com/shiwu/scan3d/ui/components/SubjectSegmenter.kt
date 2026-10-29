package com.shiwu.scan3d.ui.components

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.ByteBufferExtractor
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.components.containers.NormalizedKeypoint
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.interactivesegmenter.InteractiveSegmenter
import java.io.Closeable
import java.nio.ByteOrder

/**
 * 画面的单一主体框，坐标按送入分割的那帧图像归一化（0..1）。
 */
data class SubjectBox(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    val coverage: Float,
    val aspect: Float,
    val locked: Boolean = false
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

/**
 * 类别无关的主体分割：MagicTouch 只回答“种子点所在的那个物体在哪里”，不输出任何类别。
 * 种子点默认在画面中心，用户也可以点取景屏重新指定"要拍的东西"。
 */
class SubjectSegmenter(context: Context) : Closeable {
    private val appContext = context.applicationContext

    // 首次分析时才加载模型，避免在组合阶段阻塞主线程。
    private val lazySegmenter = lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        InteractiveSegmenter.createFromOptions(
            appContext,
            InteractiveSegmenter.InteractiveSegmenterOptions.builder()
                .setBaseOptions(
                    BaseOptions.builder()
                        .setModelAssetPath(MODEL_ASSET)
                        .setDelegate(Delegate.CPU)
                        .build()
                )
                .setOutputCategoryMask(false)
                .setOutputConfidenceMasks(true)
                .build()
        )
    }

    @Volatile private var seedX = 0.5f
    @Volatile private var seedY = 0.5f
    private var lastBox: SubjectBox? = null
    private var lockFrames = 0
    private var lastLogAt = 0L

    fun setSeed(nx: Float, ny: Float) {
        seedX = nx.coerceIn(0.01f, 0.99f)
        seedY = ny.coerceIn(0.01f, 0.99f)
        lastBox = null
        lockFrames = 0
    }

    /**
     * 同步跑一次分割，只返回主体外接框。调用方需保证在单一后台线程上调用。
     */
    fun analyze(bitmap: Bitmap): SubjectBox? {
        val segmenter = runCatching { lazySegmenter.value }.getOrElse { error ->
            Log.e(TAG, "segmenter init failed", error)
            return null
        }
        val started = SystemClock.elapsedRealtime()
        val mpImage = BitmapImageBuilder(bitmap).build()
        var masks: List<MPImage>? = null

        val fresh = try {
            // 0.10.21 的 Java SDK 漏了 point.normalized(true)，ROI 实际按像素解释；
            // 直接传当前输入图的像素坐标，否则点永远落在左上角。
            val roi = InteractiveSegmenter.RegionOfInterest.create(
                NormalizedKeypoint.create(
                    seedX * bitmap.width,
                    seedY * bitmap.height
                )
            )
            val result = segmenter.segment(mpImage, roi)
            val list = result.confidenceMasks().orElse(null) ?: return null
            masks = list
            val chosen = list[pickMaskIndex(list)]
            boxFromMask(chosen, bitmap.width.toFloat() / bitmap.height.toFloat())
        } catch (t: Throwable) {
            Log.w(TAG, "segment failed", t)
            null
        } finally {
            masks?.forEach { runCatching { it.close() } }
            mpImage.close()
        }

        val elapsed = SystemClock.elapsedRealtime() - started
        val box = fresh ?: return null
        val now = SystemClock.elapsedRealtime()
        if (now - lastLogAt > 1500) {
            lastLogAt = now
            Log.d(
                TAG,
                "seg ${elapsed}ms ${bitmap.width}x${bitmap.height} " +
                        "box=(${box.left},${box.top},${box.right},${box.bottom}) cov=${box.coverage}"
            )
        }
        return smooth(box)
    }

    /** 多张置信度 mask 时，挑种子点处置信度最高的那张。 */
    private fun pickMaskIndex(masks: List<MPImage>): Int {
        if (masks.size <= 1) return 0
        var best = 0
        var bestValue = -1f
        masks.forEachIndexed { index, mask ->
            val value = seedValue(mask)
            if (value > bestValue) {
                bestValue = value
                best = index
            }
        }
        return best
    }

    private fun seedValue(mask: MPImage): Float {
        val w = mask.width
        val h = mask.height
        if (w <= 0 || h <= 0) return -1f
        val x = (seedX * (w - 1)).toInt().coerceIn(0, w - 1)
        val y = (seedY * (h - 1)).toInt().coerceIn(0, h - 1)
        val raw = ByteBufferExtractor.extract(mask).duplicate()
        raw.rewind()
        raw.order(ByteOrder.nativeOrder())
        val floats = raw.asFloatBuffer()
        val index = y * w + x
        return if (index < floats.remaining()) floats.get(index) else -1f
    }

    private fun boxFromMask(mask: MPImage, aspect: Float): SubjectBox? {
        val w = mask.width
        val h = mask.height
        if (w <= 0 || h <= 0) return null

        val raw = ByteBufferExtractor.extract(mask).duplicate()
        raw.rewind()
        raw.order(ByteOrder.nativeOrder())
        val floats = raw.asFloatBuffer()
        val total = w * h
        if (floats.remaining() < total) return null

        // 种子点所在的连通域才是“要拍的那个物体”；阈值边缘的零散像素则整体丢弃。
        val sx = seedX * (w - 1)
        val sy = seedY * (h - 1)
        val seedIdx = sy.toInt().coerceIn(0, h - 1) * w + sx.toInt().coerceIn(0, w - 1)
        val seen = BooleanArray(total)
        val queue = IntArray(total)
        var nearest: Component? = null
        var nearestDist2 = Float.MAX_VALUE
        var seedComponent: Component? = null

        for (start in 0 until total) {
            if (seen[start] || floats.get(start) < MASK_THRESHOLD) continue
            var head = 0
            var tail = 0
            queue[tail++] = start
            seen[start] = true
            var count = 0
            var minX = w
            var minY = h
            var maxX = -1
            var maxY = -1
            var hasSeed = false

            while (head < tail) {
                val p = queue[head++]
                val x = p % w
                val y = p / w
                count++
                if (p == seedIdx) hasSeed = true
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y

                if (x > 0) {
                    val q = p - 1
                    if (!seen[q] && floats.get(q) >= MASK_THRESHOLD) {
                        seen[q] = true
                        queue[tail++] = q
                    }
                }
                if (x < w - 1) {
                    val q = p + 1
                    if (!seen[q] && floats.get(q) >= MASK_THRESHOLD) {
                        seen[q] = true
                        queue[tail++] = q
                    }
                }
                if (y > 0) {
                    val q = p - w
                    if (!seen[q] && floats.get(q) >= MASK_THRESHOLD) {
                        seen[q] = true
                        queue[tail++] = q
                    }
                }
                if (y < h - 1) {
                    val q = p + w
                    if (!seen[q] && floats.get(q) >= MASK_THRESHOLD) {
                        seen[q] = true
                        queue[tail++] = q
                    }
                }
            }

            val component = Component(count, minX, minY, maxX, maxY)
            if (hasSeed) {
                seedComponent = component
            } else {
                // 种子点没落在任何区域里（高光/缝隙）时，退到离它最近的连通域，
                // 而不是全画面最大的那块——那块往往是手或桌面。
                val dx = maxOf(0f, maxOf(minX - sx, sx - maxX))
                val dy = maxOf(0f, maxOf(minY - sy, sy - maxY))
                val dist2 = dx * dx + dy * dy
                if (dist2 < nearestDist2) {
                    nearestDist2 = dist2
                    nearest = component
                }
            }
        }

        val chosen = seedComponent ?: nearest ?: return null
        if (seedComponent == null) {
            val distance = kotlin.math.sqrt(nearestDist2) /
                    kotlin.math.hypot(w.toFloat(), h.toFloat())
            if (distance > MAX_SEED_DIST) return null
        }

        val coverage = chosen.count / total.toFloat()
        val left = chosen.minX / w.toFloat()
        val top = chosen.minY / h.toFloat()
        val right = (chosen.maxX + 1) / w.toFloat()
        val bottom = (chosen.maxY + 1) / h.toFloat()

        // 点空/点到背景时会分割出整张画面，这种结果没有意义。
        if (coverage < MIN_COVERAGE) return null
        if (right - left > 0.97f && bottom - top > 0.97f) return null
        if (coverage > MAX_COVERAGE) return null

        return SubjectBox(left, top, right, bottom, coverage, aspect)
    }

    /** 框的轻微抖动做平滑，连续几帧稳定后标记 LOCK。 */
    private fun smooth(next: SubjectBox): SubjectBox {
        val prev = lastBox
        if (prev == null || iou(prev, next) < 0.35f) {
            lastBox = next
            lockFrames = 0
            return next
        }
        lockFrames = (lockFrames + 1).coerceAtMost(6)
        val t = 0.6f
        val out = next.copy(
            left = prev.left + (next.left - prev.left) * t,
            top = prev.top + (next.top - prev.top) * t,
            right = prev.right + (next.right - prev.right) * t,
            bottom = prev.bottom + (next.bottom - prev.bottom) * t,
            locked = lockFrames >= 2
        )
        lastBox = out
        return out
    }

    private fun iou(a: SubjectBox, b: SubjectBox): Float {
        val x1 = maxOf(a.left, b.left)
        val y1 = maxOf(a.top, b.top)
        val x2 = minOf(a.right, b.right)
        val y2 = minOf(a.bottom, b.bottom)
        val inter = maxOf(0f, x2 - x1) * maxOf(0f, y2 - y1)
        val union = a.width * a.height + b.width * b.height - inter
        return if (union <= 0f) 0f else inter / union
    }

    override fun close() {
        runCatching {
            if (lazySegmenter.isInitialized()) lazySegmenter.value.close()
        }
    }

    private data class Component(
        val count: Int,
        val minX: Int,
        val minY: Int,
        val maxX: Int,
        val maxY: Int
    )

    private companion object {
        const val TAG = "SubjectSegmenter"
        const val MODEL_ASSET = "magic_touch.tflite"
        const val MASK_THRESHOLD = 0.65f
        const val MIN_COVERAGE = 0.004f
        const val MAX_COVERAGE = 0.6f
        const val MAX_SEED_DIST = 0.08f
    }
}
