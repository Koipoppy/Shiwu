package com.shiwu.scan3d.recon

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import java.io.File
import com.shiwu.scan3d.data.ApiException
import com.shiwu.scan3d.ui.components.SubjectSegmenter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.calib3d.Calib3d
import org.opencv.core.CvException
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfDMatch
import org.opencv.core.MatOfDouble
import org.opencv.core.MatOfKeyPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.MatOfPoint3f
import org.opencv.core.Point
import org.opencv.core.Point3
import org.opencv.features2d.DescriptorMatcher
import org.opencv.features2d.ORB
import org.opencv.imgproc.Imgproc

/**
 * 本地引擎：未知位姿的随意拍摄 →
 *   OpenCV 增量式 SfM 解相机位姿与稀疏点云 →
 *   视觉外壳（剪影雕刻）→ Surface Nets 网格 → 顶点色 → GLB。
 */
object LocalReconstructor {
    private const val TAG = "LocalRecon"

    private const val MAX_SIDE = 1024
    private const val GRID_N = 112
    private const val ORB_FEATURES = 1500

    /** 一对底片要算成立体像对，至少要有这么多极线内点。 */
    private const val MIN_PAIR_INLIERS = 25

    /** 少于这么多台相机就雕不出东西，宁可报错让用户重拍。 */
    private const val MIN_CAMERAS = 3

    /** 三角化/重投影允许的像素误差。 */
    private const val MAX_REPROJ_PX = 10.0
    private const val PNP_REPROJ_PX = 8.0f

    /** 解一帧位姿至少要有这么多 PnP 内点，否则位姿不可信。 */
    private const val MIN_POSE_INLIERS = 10

    private val IDENTITY_R = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    private val ZERO_T = doubleArrayOf(0.0, 0.0, 0.0)

    data class FrameData(
        val path: String,
        val color: Bitmap,
        val gray: Mat,
        val mask: Mat,
        val maskPixels: ByteArray,
        val pixels: IntArray
    )

    data class CameraPose(
        val index: Int,
        val kr: DoubleArray,   // K * R
        val kt: DoubleArray,   // K * t
        val r: DoubleArray,    // world -> cam
        val t: DoubleArray,
        val center: DoubleArray
    )

    data class SfmOutput(
        val cameras: List<CameraPose>,
        val points: List<DoubleArray>,
        val k: Mat
    )

    fun loadUpright(path: String, maxSide: Int = MAX_SIDE): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) throw ApiException("底片读取失败")
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxSide && bounds.outHeight / (sample * 2) >= maxSide) {
            sample *= 2
        }
        val decoded = BitmapFactory.decodeFile(
            path, BitmapFactory.Options().apply { inSampleSize = sample }
        ) ?: throw ApiException("底片读取失败")
        val orientation = ExifInterface(path)
            .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        val rotated = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotate(decoded, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotate(decoded, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotate(decoded, 270f)
            else -> decoded
        }
        val longest = maxOf(rotated.width, rotated.height)
        return if (longest > maxSide) {
            val ratio = maxSide.toFloat() / longest
            Bitmap.createScaledBitmap(
                rotated,
                (rotated.width * ratio).toInt().coerceAtLeast(1),
                (rotated.height * ratio).toInt().coerceAtLeast(1),
                true
            )
        } else {
            rotated
        }
    }

    private fun rotate(bmp: Bitmap, deg: Float): Bitmap {
        val m = android.graphics.Matrix().apply { postRotate(deg) }
        return Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    }

    /** 没有分割结果时的兜底剪影：中央椭圆。 */
    fun ellipseMask(bmp: Bitmap): Bitmap {
        val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(out)
        val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
        }
        canvas.drawOval(
            android.graphics.RectF(
                bmp.width * 0.14f, bmp.height * 0.10f,
                bmp.width * 0.86f, bmp.height * 0.90f
            ),
            paint
        )
        return out
    }

    fun maskToBinary(maskBmp: Bitmap): Mat {
        val rgba = Mat()
        Utils.bitmapToMat(maskBmp, rgba)
        val gray = Mat()
        Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
        rgba.release()
        val bin = Mat()
        Imgproc.threshold(gray, bin, 127.0, 255.0, Imgproc.THRESH_BINARY)
        gray.release()
        return bin
    }

    /**
     * 完整离线重建。frames 是底片路径；masks 为空时用中央椭圆兜底。
     * 返回写好的 GLB 文件。
     */
    suspend fun reconstruct(
        frames: List<String>,
        segmenter: SubjectSegmenter?,
        seed: Pair<Float, Float>,
        useSegmenter: Boolean,
        maskFiles: List<String>? = null,
        target: File,
        onProgress: (Float, String) -> Unit
    ): File = withContext(Dispatchers.Default) {
        try {
            runReconstruct(frames, segmenter, seed, useSegmenter, maskFiles, target, onProgress)
        } catch (c: CancellationException) {
            throw c
        } catch (e: ApiException) {
            throw e
        } catch (e: CvException) {
            // OpenCV 抛出来的英文断言对用户没有意义，收敛成一句可操作的话
            Log.e(TAG, "opencv 调用失败", e)
            throw ApiException("本地引擎解析底片出错，请重拍一组更清晰的底片")
        } catch (e: OutOfMemoryError) {
            Log.e(TAG, "内存不足", e)
            throw ApiException("底片太大，内存不够，请少拍几帧")
        } catch (e: Throwable) {
            Log.e(TAG, "本地引擎失败", e)
            throw ApiException("本地引擎出错：${e.javaClass.simpleName}")
        }
    }

    private suspend fun runReconstruct(
        frames: List<String>,
        segmenter: SubjectSegmenter?,
        seed: Pair<Float, Float>,
        useSegmenter: Boolean,
        maskFiles: List<String>?,
        target: File,
        onProgress: (Float, String) -> Unit
    ): File {
        if (frames.size < 3) throw ApiException("至少需要 3 张底片才能离线重建")
        if (!OpenCVLoader.initLocal()) throw ApiException("本地引擎初始化失败")

        // 1) 读图 + mask
        onProgress(0.04f, "读取底片")
        val framesData = frames.mapIndexed { index, path ->
            val color = loadUpright(path)
            val maskBmp = maskFiles?.getOrNull(index)?.let { BitmapFactory.decodeFile(it) }
                ?: (if (useSegmenter && segmenter != null) {
                    segmenter.segmentMask(color, seed.first, seed.second)
                } else null)
            val finalMask = maskBmp ?: ellipseMask(color)
            val gray = Mat()
            val rgba = Mat()
            Utils.bitmapToMat(color, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            rgba.release()
            val maskBin = maskToBinary(finalMask)
            maskBmp?.recycle()
            val px = IntArray(color.width * color.height)
            color.getPixels(px, 0, color.width, 0, 0, color.width, color.height)
            FrameData(
                path, color, gray, maskBin,
                ByteArray(px.size) { (px[it].toLong() and 0xFF).toByte() },
                px
            )
        }

        try {
            // 2) 特征
            onProgress(0.10f, "提取特征")
            val n = framesData.size
            val descriptors = Array(n) { Mat() }
            val kps = arrayOfNulls<Array<Point>>(n)
            val orb = ORB.create(ORB_FEATURES, 1.2f, 8, 31, 0, 2, ORB.HARRIS_SCORE, 31, 20)
            // 特征要在整幅画面上提：主体常常是一身光滑的东西（玩偶/杯子），
            // 真正可靠的特征在桌面和背景上。mask 只用来雕刻剪影，不参与特征提取。
            val noMask = Mat()
            for (i in 0 until n) {
                val kp = MatOfKeyPoint()
                orb.detectAndCompute(framesData[i].gray, noMask, kp, descriptors[i])
                kps[i] = readKeypoints(kp)
                kp.release()
                Log.i(TAG, "frame $i features=${kps[i]?.size}")
            }
            noMask.release()

            // 3) 全对匹配 + 几何校验
            onProgress(0.18f, "匹配特征")
            val w = framesData[0].color.width.toDouble()
            val h = framesData[0].color.height.toDouble()
            val fx = w * 1.05
            val kMat = Mat(3, 3, CvType.CV_64FC1).apply {
                put(0, 0, fx, 0.0, w / 2.0, 0.0, fx, h / 2.0, 0.0, 0.0, 1.0)
            }
            val matcher = DescriptorMatcher.create(DescriptorMatcher.BRUTEFORCE_HAMMING)

            data class PairGood(
                val i: Int, val j: Int, val inliers: Int,
                /** 只保留极线/手性检验通过的内点。 */
                val qi: IntArray, val ti: IntArray,
                val r: DoubleArray?, val t: DoubleArray?
            )

            val pairs = HashMap<Int, PairGood>()
            for (a in 0 until n) {
                for (b in (a + 1) until n) {
                    if (descriptors[a].empty() || descriptors[b].empty()) continue
                    val knn = ArrayList<MatOfDMatch>()
                    matcher.knnMatch(descriptors[a], descriptors[b], knn, 2)
                    val qAll = ArrayList<Int>()
                    val tAll = ArrayList<Int>()
                    for (list in knn) {
                        val dm = list.toArray()
                        if (dm.size < 2) continue
                        if (dm[0].distance < 0.85f * dm[1].distance) {
                            qAll.add(dm[0].queryIdx)
                            tAll.add(dm[0].trainIdx)
                        }
                    }
                    knn.forEach { runCatching { it.release() } }
                    if (qAll.size < 15) continue
                    val kpA = kps[a] ?: continue
                    val kpB = kps[b] ?: continue
                    val pA = MatOfPoint2f(*Array(qAll.size) { k ->
                        Point(kpA[qAll[k]].x, kpA[qAll[k]].y)
                    })
                    val pB = MatOfPoint2f(*Array(qAll.size) { k ->
                        Point(kpB[tAll[k]].x, kpB[tAll[k]].y)
                    })
                    val e = Calib3d.findEssentialMat(pA, pB, kMat, Calib3d.RANSAC, 0.999, 1.5, 500)
                    if (e.empty()) {
                        pA.release(); pB.release(); e.release(); continue
                    }
                    val rOut = Mat()
                    val tOut = Mat()
                    val maskPose = Mat()
                    // recoverPose 的返回值就是通过极线/手性检验的内点数
                    val inliers = Calib3d.recoverPose(e, pA, pB, kMat, rOut, tOut, maskPose)
                    val keep = BooleanArray(qAll.size) { true }
                    if (maskPose.total() == qAll.size.toLong()) {
                        val maskArr = ByteArray(qAll.size)
                        maskPose.get(0, 0, maskArr)
                        for (m in maskArr.indices) keep[m] = maskArr[m].toInt() != 0
                    }
                    val qi = ArrayList<Int>(qAll.size)
                    val ti = ArrayList<Int>(qAll.size)
                    for (m in qAll.indices) if (keep[m]) { qi.add(qAll[m]); ti.add(tAll[m]) }
                    var r: DoubleArray? = null
                    var t: DoubleArray? = null
                    if (inliers >= MIN_PAIR_INLIERS) {
                        // 平移只有单位尺度，r/t 留给增量注册阶段用，这里立刻拷成基本类型释放 Mat
                        r = readMat(rOut, 9)
                        t = readMat(tOut, 3)
                    }
                    Log.i(TAG, "pair $a-$b ratioGood=${qAll.size} inliers=$inliers pose=${if (r != null) 1 else 0}")
                    pairs[a * 100 + b] = PairGood(a, b, inliers, qi.toIntArray(), ti.toIntArray(), r, t)
                    pA.release(); pB.release()
                    rOut.release(); tOut.release(); maskPose.release(); e.release()
                }
            }

            val best = pairs.values.maxByOrNull { it.inliers }
                ?: throw ApiException("底片特征不足，请拍清晰一些")
            Log.i(TAG, "best pair ${best.i}-${best.j} inliers=${best.inliers} (of ${pairs.size} pairs)")
            if (best.inliers < MIN_PAIR_INLIERS) throw ApiException("照片之间重合度不够，请围绕同一物体多拍几个角度")
            val bestR = best.r ?: throw ApiException("未能解算相机位姿")
            val bestT = best.t ?: throw ApiException("未能解算相机位姿")

            // 4) 增量注册
            onProgress(0.34f, "解算相机位姿")
            val cameras = arrayOfNulls<CameraPose>(n)
            val kpToPoint = Array(n) { IntArray(kps[it]?.size ?: 0) { -1 } }
            val points = ArrayList<DoubleArray>()

            fun pairData(a: Int, b: Int): PairGood? = pairs[minOf(a, b) * 100 + maxOf(a, b)]

            fun correspondences(k: Int, r: Int): List<Pair<Int, Int>> {
                val pd = pairData(k, r) ?: return emptyList()
                val out = ArrayList<Pair<Int, Int>>()
                for (m in pd.qi.indices) {
                    val ik = if (pd.i == k) pd.qi[m] else pd.ti[m]
                    val ir = if (pd.i == k) pd.ti[m] else pd.qi[m]
                    out.add(ik to ir)
                }
                return out
            }

            var triAttempt = 0
            var triAccepted = 0
            val triStats = IntArray(4)   // 退化 / 手性 / 重投影 / 通过

            fun makePose(index: Int, rArr: DoubleArray, tArr: DoubleArray): CameraPose {
                val kArr = readMat(kMat, 9)
                val kr = DoubleArray(9)
                val kt = DoubleArray(3)
                for (row in 0..2) {
                    for (col in 0..2) {
                        var s = 0.0
                        for (k in 0..2) s += kArr[row * 3 + k] * rArr[k * 3 + col]
                        kr[row * 3 + col] = s
                    }
                    var s = 0.0
                    for (k in 0..2) s += kArr[row * 3 + k] * tArr[k]
                    kt[row] = s
                }
                // center = -R^T * t
                val center = DoubleArray(3)
                for (row in 0..2) {
                    var s = 0.0
                    for (k in 0..2) s += rArr[k * 3 + row] * tArr[k]
                    center[row] = -s
                }
                return CameraPose(index, kr, kt, rArr.clone(), tArr.clone(), center)
            }

            fun registerPairPoints(a: Int, b: Int, camA: CameraPose, camB: CameraPose) {                val pd = pairData(a, b) ?: return
                val kpA = kps[a] ?: return
                val kpB = kps[b] ?: return
                val pA = ArrayList<Point>()
                val pB = ArrayList<Point>()
                val newKp = ArrayList<Pair<Int, Int>>()
                for (m in pd.qi.indices) {
                    val ia = if (pd.i == a) pd.qi[m] else pd.ti[m]
                    val ib = if (pd.i == a) pd.ti[m] else pd.qi[m]
                    if (kpToPoint[a][ia] >= 0 || kpToPoint[b][ib] >= 0) continue
                    pA.add(kpA[ia])
                    pB.add(kpB[ib])
                    newKp.add(ia to ib)
                }
                if (pA.size < 6) return
                triAttempt += pA.size
                val world = triangulate(kMat, camA, camB, pA, pB, triStats)
                world.forEachIndexed { idx, pt ->
                    if (pt == null) return@forEachIndexed
                    triAccepted++
                    val pid = points.size
                    points.add(pt)
                    kpToPoint[a][newKp[idx].first] = pid
                    kpToPoint[b][newKp[idx].second] = pid
                }
            }

            /** 把所有已注册的相机两两三角化，尽快把点云铺满每帧的可见区域。 */
            fun triangulateAll() {
                val regs = (0 until n).filter { cameras[it] != null }
                for (i in regs.indices) {
                    for (j in (i + 1) until regs.size) {
                        val a = regs[i]
                        val b = regs[j]
                        registerPairPoints(a, b, cameras[a]!!, cameras[b]!!)
                    }
                }
                Log.i(
                    TAG,
                    "triangulate attempted=$triAttempt accepted=$triAccepted total=${points.size} " +
                            "degenerate=${triStats[0]} cheirality=${triStats[1]} reproj=${triStats[2]}"
                )
            }

            cameras[best.i] = makePose(best.i, IDENTITY_R, ZERO_T)
            cameras[best.j] = makePose(best.j, bestR, bestT)
            triangulateAll()
            var registeredCount = 2
            Log.i(TAG, "seed pair ${best.i}-${best.j} points=${points.size}")

            /** 用已注册帧上的 3D 点给第 k 帧解位姿；解不出来返回 null。 */
            fun solvePose(k: Int, partner: Int): Pair<CameraPose, List<Triple<Int, Int, Int>>>? {
                val corr = correspondences(k, partner).mapNotNull { (ik, ir) ->
                    val pid = kpToPoint[partner][ir]
                    if (pid < 0) null else Triple(ik, ir, pid)
                }
                if (corr.size < 8) return null
                val imgPts = MatOfPoint2f(*Array(corr.size) { m -> kps[k]!![corr[m].first] })
                val objPts = MatOfPoint3f(*Array(corr.size) { m ->
                    val p = points[corr[m].third]
                    Point3(p[0], p[1], p[2])
                })
                val rvec = Mat(3, 1, CvType.CV_64FC1)
                val tvec = Mat(3, 1, CvType.CV_64FC1)
                val inlierMat = Mat()
                val ok = try {
                    Calib3d.solvePnPRansac(
                        objPts, imgPts, kMat, MatOfDouble(), rvec, tvec,
                        false, 500, PNP_REPROJ_PX, 0.999, inlierMat
                    )
                } catch (e: CvException) {
                    Log.w(TAG, "solvePnP threw for frame $k: ${e.message}")
                    false
                } finally {
                    objPts.release()
                    imgPts.release()
                }
                val inliers = inlierMat.total().toInt()
                Log.i(TAG, "pnp k=$k partner=$partner corr=${corr.size} ok=$ok inliers=$inliers")
                inlierMat.release()
                // 内点太少或占比过低都说明这组对应关系兜不住一个可信位姿
                if (!ok || inliers < MIN_POSE_INLIERS || inliers * 5 < corr.size) {
                    rvec.release(); tvec.release()
                    return null
                }
                val r = Mat(3, 3, CvType.CV_64FC1)
                Calib3d.Rodrigues(rvec, r)
                val pose = makePose(k, readMat(r, 9), readMat(tvec, 3))
                r.release(); rvec.release(); tvec.release()
                return pose to corr
            }

            while (registeredCount < n) {
                // 按与已注册帧的重合度从高到低试，某一对解不出来就换下一对
                val candidates = ArrayList<Triple<Int, Int, Int>>()
                for (k in 0 until n) {
                    if (cameras[k] != null) continue
                    for (r in 0 until n) {
                        if (cameras[r] == null) continue
                        // 只看已注册侧是否有点：未注册图像的关键点本来就没有 3D 点
                        val count = correspondences(k, r).count { (_, ir) -> kpToPoint[r][ir] >= 0 }
                        if (count >= 8) candidates.add(Triple(k, r, count))
                    }
                }
                candidates.sortByDescending { it.third }

                var solvedPose: CameraPose? = null
                var solvedCorr: List<Triple<Int, Int, Int>>? = null
                var solvedPartner = -1
                for (candidate in candidates) {
                    val result = solvePose(candidate.first, candidate.second) ?: continue
                    solvedPose = result.first
                    solvedCorr = result.second
                    solvedPartner = candidate.second
                    break
                }
                val pose = solvedPose
                val corr = solvedCorr
                if (pose == null || corr == null) {
                    Log.w(TAG, "registration stalled at $registeredCount/$n, points=${points.size}")
                    break
                }
                val frameIdx = pose.index
                cameras[frameIdx] = pose
                corr.forEach { (ik, _, pid) -> kpToPoint[frameIdx][ik] = pid }
                triangulateAll()
                registeredCount++
                onProgress(0.34f + 0.16f * registeredCount / n, "解算相机位姿 ${registeredCount}/$n")
            }

            val registeredIdx = (0 until n).filter { cameras[it] != null }
            Log.i(TAG, "registered=${registeredIdx.size}/$n points=${points.size}")
            if (registeredIdx.size < MIN_CAMERAS) {
                throw ApiException("照片之间关联不足，请围绕同一物体多拍几帧清晰底片")
            }
            if (points.size < 12) throw ApiException("重建点云过于稀疏，请多拍几帧")
            ensureParallax(registeredIdx.map { cameras[it]!! }, points)

            // 5) 视觉外壳
            onProgress(0.56f, "雕刻模型")
            val cams = registeredIdx.map { cameras[it]!! }
            val usedFrames = registeredIdx.map { framesData[it] }
            val field = carve(cams, usedFrames, points)

            // 6) 网格
            onProgress(0.72f, "生成网格")
            val mesh = SurfaceNets.build(field, GRID_N, GRID_N, GRID_N)
            if (mesh.indices.isEmpty()) throw ApiException("模型生成失败，请换一组底片")

            // 7) 顶点色 + 归一化（上色必须在归一化之前，相机矩阵是 SfM 原始尺度的）
            onProgress(0.88f, "上色与导出")
            val colors = paintColors(mesh, cams, usedFrames)
            normalize(mesh.positions)

            // 8) GLB
            if (!GlbWriter.write(target, mesh.positions, mesh.normals, colors, mesh.indices)) {
                throw ApiException("模型导出失败")
            }
            onProgress(1f, "完成")
            return target
        } finally {
            framesData.forEach {
                runCatching { it.gray.release() }
                runCatching { it.mask.release() }
                runCatching { it.color.recycle() }
            }
        }
    }

    /**
     * 两视图三角化，返回世界坐标；手性/重投影不合格的点返回 null。
     *
     * 在 A 的相机坐标系里做闭式解（A 系下射线方向就是归一化坐标本身），
     * 比 DLT 省一次 SVD，也避开 OpenCV Mat 的按行连续读写陷阱。
     */
    private fun triangulate(
        k: Mat,
        camA: CameraPose,
        camB: CameraPose,
        ptsA: List<Point>,
        ptsB: List<Point>,
        stats: IntArray = IntArray(4)
    ): List<DoubleArray?> {
        val kArr = readMat(k, 9)
        val fx = kArr[0]
        val fy = kArr[4]
        val cx = kArr[2]
        val cy = kArr[5]

        // B 在 A 系下的相对位姿：X_b = rel * X_a + relT
        val ra = camA.r
        val ta = camA.t
        val rb = camB.r
        val tb = camB.t
        val rel = DoubleArray(9)
        for (row in 0..2) {
            for (col in 0..2) {
                var s = 0.0
                for (m in 0..2) s += rb[row * 3 + m] * ra[col * 3 + m]
                rel[row * 3 + col] = s
            }
        }
        val relT = DoubleArray(3)
        for (row in 0..2) {
            var s = tb[row]
            for (m in 0..2) s -= rel[row * 3 + m] * ta[m]
            relT[row] = s
        }

        val out = ArrayList<DoubleArray?>(ptsA.size)
        for (i in ptsA.indices) {
            val x1 = (ptsA[i].x - cx) / fx
            val y1 = (ptsA[i].y - cy) / fy
            val x2 = (ptsB[i].x - cx) / fx
            val y2 = (ptsB[i].y - cy) / fy

            // A 系下指向该点的射线是 s * (x1, y1, 1)，代进 B 的投影方程即可解出 s
            val r1d = rel[0] * x1 + rel[1] * y1 + rel[2]
            val r2d = rel[3] * x1 + rel[4] * y1 + rel[5]
            val r3d = rel[6] * x1 + rel[7] * y1 + rel[8]
            val a1 = x2 * r3d - r1d
            val b1 = relT[0] - x2 * relT[2]
            val a2 = y2 * r3d - r2d
            val b2 = relT[1] - y2 * relT[2]
            val denom = a1 * a1 + a2 * a2
            if (denom < 1e-12) { stats[0]++; out.add(null); continue }
            val s = (a1 * b1 + a2 * b2) / denom
            if (!s.isFinite() || s <= 1e-6) { stats[1]++; out.add(null); continue }

            val az = s
            val ax = s * x1
            val ay = s * y1
            val zb = rel[6] * ax + rel[7] * ay + rel[8] * az + relT[2]
            if (!zb.isFinite() || zb <= 1e-6) { stats[1]++; out.add(null); continue }

            // 两视图重投影都要说得通，否则这一对匹配是错的
            val u1 = fx * x1 + cx
            val v1 = fy * y1 + cy
            val u2 = fx * (rel[0] * ax + rel[1] * ay + rel[2] * az + relT[0]) / zb + cx
            val v2 = fy * (rel[3] * ax + rel[4] * ay + rel[5] * az + relT[1]) / zb + cy
            val e1 = kotlin.math.hypot(u1 - ptsA[i].x, v1 - ptsA[i].y)
            val e2 = kotlin.math.hypot(u2 - ptsB[i].x, v2 - ptsB[i].y)
            if (e1 > MAX_REPROJ_PX || e2 > MAX_REPROJ_PX) { stats[2]++; out.add(null); continue }
            stats[3]++

            // 回到世界坐标：X = R_A^T (X_a - t_A)
            val dx = ax - ta[0]
            val dy = ay - ta[1]
            val dz = az - ta[2]
            out.add(
                doubleArrayOf(
                    ra[0] * dx + ra[3] * dy + ra[6] * dz,
                    ra[1] * dx + ra[4] * dy + ra[7] * dz,
                    ra[2] * dx + ra[5] * dy + ra[8] * dz
                )
            )
        }
        return out
    }

    /**
     * 位姿是否只解出了“原地转圈”：相机之间的最大位移和物体深度比起来太小的话，
     * 三角化出来的点会全在无穷远，模型只会碎成一片。这种底片怎么修算法都救不回来，
     * 只能提示用户改拍法。
     */
    private fun ensureParallax(cams: List<CameraPose>, points: List<DoubleArray>) {
        if (cams.size < 2 || points.isEmpty()) return
        var maxBaseline = 0.0
        for (i in cams.indices) {
            for (j in (i + 1) until cams.size) {
                val a = cams[i].center
                val b = cams[j].center
                val d = kotlin.math.sqrt(
                    (a[0] - b[0]) * (a[0] - b[0]) +
                            (a[1] - b[1]) * (a[1] - b[1]) +
                            (a[2] - b[2]) * (a[2] - b[2])
                )
                if (d.isFinite() && d > maxBaseline) maxBaseline = d
            }
        }
        val depths = points.mapNotNull { p ->
            val xs = DoubleArray(3)
            // 点深 = 到最近相机的距离
            var best = Double.MAX_VALUE
            for (cam in cams) {
                val dx = p[0] - cam.center[0]
                val dy = p[1] - cam.center[1]
                val dz = p[2] - cam.center[2]
                val d = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                if (d.isFinite() && d < best) best = d
            }
            if (best == Double.MAX_VALUE) null else best
        }.sorted()
        if (depths.isEmpty()) return
        val median = depths[depths.size / 2]
        val ratio = if (median > 1e-9) maxBaseline / median else 1.0
        Log.i(TAG, "parallax baseline=$maxBaseline medianDepth=$median ratio=$ratio")
        if (ratio < 0.012) {
            throw ApiException("这批底片几乎是原地旋转：请保持物体不动、绕它走一圈再拍（或把物体放在标记板上转）")
        }
    }

    /**
     * 按行连续读出 Mat 头顶的 count 个元素。Mat.put/get(row, col, data) 在 Java 绑定里是
     * “从 (row,col) 起按行连续写”而不是写一列，所以只对 (0,0) 起读整个连续矩阵是安全的。
     */
    private fun readMat(m: Mat, count: Int): DoubleArray {
        val out = DoubleArray(count)
        if (count == 0) return out
        if (m.depth() == CvType.CV_32F) {
            val f = FloatArray(count)
            m.get(0, 0, f)
            for (i in 0 until count) out[i] = f[i].toDouble()
        } else {
            m.get(0, 0, out)
        }
        return out
    }

    /**
     * 直接从 KeyPoint 矩阵取点。OpenCV 的 KeyPoint 是 N×1 的 CV_32FC7
     * （x, y, size, angle, response, octave, class_id）。走 toArray() 的话，
     * 一旦某帧凑不出特征点，converters.cpp 会抛 CvException。
     */
    private fun readKeypoints(kp: MatOfKeyPoint): Array<Point> {
        val count = kp.rows()
        if (count <= 0) return emptyArray()
        val buf = FloatArray(count * 7)
        kp.get(0, 0, buf)
        return Array(count) { i -> Point(buf[i * 7].toDouble(), buf[i * 7 + 1].toDouble()) }
    }

    private suspend fun carve(
        cams: List<CameraPose>,
        frames: List<FrameData>,
        points: List<DoubleArray>
    ): FloatArray {
        val n = GRID_N

        data class CamProj(
            val kr: DoubleArray, val kt: DoubleArray,
            val w: Int, val h: Int,
            val mask: ByteArray, val pixels: IntArray
        )
        val projs = cams.mapIndexed { idx, cam ->
            val f = frames[idx]
            CamProj(cam.kr, cam.kt, f.color.width, f.color.height, f.maskPixels, f.pixels)
        }

        fun insideMask(c: CamProj, u: Double, v: Double): Boolean {
            val xi = u.toInt()
            val yi = v.toInt()
            if (xi < 0 || yi < 0 || xi >= c.w || yi >= c.h) return false
            return c.mask[yi * c.w + xi].toInt() and 0xFF != 0
        }

        /** 点在这台相机的主体剪影里吗？投影不到画面里就返回 null。 */
        fun silhouette(c: CamProj, wx: Double, wy: Double, wz: Double): Boolean? {
            val xc = c.kr[0] * wx + c.kr[1] * wy + c.kr[2] * wz + c.kt[0]
            val yc = c.kr[3] * wx + c.kr[4] * wy + c.kr[5] * wz + c.kt[1]
            val zc = c.kr[6] * wx + c.kr[7] * wy + c.kr[8] * wz + c.kt[2]
            if (zc <= 1e-6) return null
            val u = xc / zc
            val v = yc / zc
            if (u < 0.0 || v < 0.0 || u >= c.w || v >= c.h) return null
            return insideMask(c, u, v)
        }

        // 特征点是在整幅画面上提的，大部分落在桌面/显示器这些背景上。
        // 只有“在每一台能看到它的相机里都落在主体剪影内”的点才属于物体，
        // 用它们定雕刻范围，否则包围盒会被背景撑爆、体素变粗。
        val objectPoints = points.filter { p ->
            var seen = false
            for (c in projs) {
                val inside = silhouette(c, p[0], p[1], p[2]) ?: continue
                if (!inside) return@filter false
                seen = true
            }
            seen
        }
        val basis = if (objectPoints.size >= 30) objectPoints else points
        Log.i(TAG, "carve points=${points.size} object=${objectPoints.size}")

        val xs = basis.map { it[0] }.sorted()
        val ys = basis.map { it[1] }.sorted()
        val zs = basis.map { it[2] }.sorted()
        fun pct(list: List<Double>, p: Double) =
            list[((list.size - 1) * p).toInt().coerceIn(0, list.size - 1)]
        val cx = (pct(xs, 0.02) + pct(xs, 0.98)) / 2.0
        val cy = (pct(ys, 0.02) + pct(ys, 0.98)) / 2.0
        val cz = (pct(zs, 0.02) + pct(zs, 0.98)) / 2.0
        // 立方体要罩住整片点云：取三轴跨度的外包，再留一点余量（视觉外壳会略微鼓出点云）
        val ex = (pct(xs, 0.98) - pct(xs, 0.02)).coerceAtLeast(1e-3)
        val ey = (pct(ys, 0.98) - pct(ys, 0.02)).coerceAtLeast(1e-3)
        val ez = (pct(zs, 0.98) - pct(zs, 0.02)).coerceAtLeast(1e-3)
        val half = maxOf(ex, ey, ez) * 0.62
        val min = doubleArrayOf(cx - half, cy - half, cz - half)
        val step = (2.0 * half) / n
        Log.i(TAG, "carve box center=($cx,$cy,$cz) half=$half")

        val field = FloatArray(n * n * n) { -1f }
        val slices = Array(n) { ByteArray(n * n) }
        runCatching {
            coroutineScope {
                (0 until n).map { z ->
                    async(Dispatchers.Default) {
                        for (y in 0 until n) {
                            for (x in 0 until n) {
                                val wx = min[0] + (x + 0.5) * step
                                val wy = min[1] + (y + 0.5) * step
                                val wz = min[2] + (z + 0.5) * step
                                var seen = false
                                var occluded = false
                                for (c in projs) {
                                    val xc = c.kr[0] * wx + c.kr[1] * wy + c.kr[2] * wz + c.kt[0]
                                    val yc = c.kr[3] * wx + c.kr[4] * wy + c.kr[5] * wz + c.kt[1]
                                    val zc = c.kr[6] * wx + c.kr[7] * wy + c.kr[8] * wz + c.kt[2]
                                    if (zc <= 1e-6) continue
                                    val u = xc / zc
                                    val v = yc / zc
                                    if (u < 0.0 || v < 0.0 || u >= c.w || v >= c.h) continue
                                    seen = true
                                    if (!insideMask(c, u, v)) {
                                        occluded = true
                                        break
                                    }
                                }
                                slices[z][y * n + x] = if (seen && !occluded) 1 else 0
                            }
                        }
                    }
                }.awaitAll()
            }
        }

        for (z in 0 until n) {
            for (y in 0 until n) {
                for (x in 0 until n) {
                    field[x + y * n + z * n * n] =
                        if (slices[z][y * n + x].toInt() != 0) 1f else -1f
                }
            }
        }
        return field
    }

    private fun normalize(positions: FloatArray) {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE; var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE; var maxZ = -Float.MAX_VALUE
        var i = 0
        while (i < positions.size) {
            if (positions[i] < minX) minX = positions[i]
            if (positions[i + 1] < minY) minY = positions[i + 1]
            if (positions[i + 2] < minZ) minZ = positions[i + 2]
            if (positions[i] > maxX) maxX = positions[i]
            if (positions[i + 1] > maxY) maxY = positions[i + 1]
            if (positions[i + 2] > maxZ) maxZ = positions[i + 2]
            i += 3
        }
        val cx = (minX + maxX) / 2f
        val cy = (minY + maxY) / 2f
        val cz = (minZ + maxZ) / 2f
        val scale = 1.0f / maxOf(maxX - minX, maxY - minY, maxZ - minZ).coerceAtLeast(1e-6f)
        i = 0
        while (i < positions.size) {
            positions[i] = (positions[i] - cx) * scale
            positions[i + 1] = (positions[i + 1] - cy) * scale
            positions[i + 2] = (positions[i + 2] - cz) * scale
            i += 3
        }
    }

    /** 每个顶点取“最正对它”的那一帧的颜色。 */
    private fun paintColors(
        mesh: SurfaceNets.Mesh,
        cams: List<CameraPose>,
        frames: List<FrameData>
    ): FloatArray {
        val colors = FloatArray(mesh.positions.size)
        val projs = cams.mapIndexed { idx, cam ->
            Triple(cam.kr, cam.kt, frames[idx])
        }

        for (v in 0 until mesh.positions.size / 3) {
            val vx = mesh.positions[v * 3].toDouble()
            val vy = mesh.positions[v * 3 + 1].toDouble()
            val vz = mesh.positions[v * 3 + 2].toDouble()
            val nx = mesh.normals[v * 3].toDouble()
            val ny = mesh.normals[v * 3 + 1].toDouble()
            val nz = mesh.normals[v * 3 + 2].toDouble()

            var bestScore = -2.0
            var bestColor = floatArrayOf(0.6f, 0.55f, 0.45f)
            for (c in projs.indices) {
                val (kr, kt, frame) = projs[c]
                val w = frame.color.width
                val h = frame.color.height
                val xc = kr[0] * vx + kr[1] * vy + kr[2] * vz + kt[0]
                val yc = kr[3] * vx + kr[4] * vy + kr[5] * vz + kt[1]
                val zc = kr[6] * vx + kr[7] * vy + kr[8] * vz + kt[2]
                if (zc <= 1e-6) continue
                val u = (xc / zc).toInt()
                val vi = (yc / zc).toInt()
                if (u < 0 || vi < 0 || u >= w || vi >= h) continue
                if (frame.maskPixels[vi * w + u].toInt() and 0xFF == 0) continue

                val cx = camCenterX(cams[c])
                val cy = camCenterY(cams[c])
                val cz = camCenterZ(cams[c])
                val dx = cx - vx; val dy = cy - vy; val dz = cz - vz
                val dl = kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
                if (dl < 1e-9) continue
                val score = (nx * dx + ny * dy + nz * dz) / dl
                if (score > bestScore) {
                    bestScore = score
                    val px = frame.pixels[vi * w + u]
                    bestColor = floatArrayOf(
                        ((px shr 16) and 0xFF) / 255f,
                        ((px shr 8) and 0xFF) / 255f,
                        (px and 0xFF) / 255f
                    )
                }
            }
            colors[v * 3] = bestColor[0]
            colors[v * 3 + 1] = bestColor[1]
            colors[v * 3 + 2] = bestColor[2]
        }
        return colors
    }

    private fun camCenterX(cam: CameraPose) =
        -(cam.r[0] * cam.t[0] + cam.r[1] * cam.t[1] + cam.r[2] * cam.t[2])

    private fun camCenterY(cam: CameraPose) =
        -(cam.r[3] * cam.t[0] + cam.r[4] * cam.t[1] + cam.r[5] * cam.t[2])

    private fun camCenterZ(cam: CameraPose) =
        -(cam.r[6] * cam.t[0] + cam.r[7] * cam.t[1] + cam.r[8] * cam.t[2])
}
