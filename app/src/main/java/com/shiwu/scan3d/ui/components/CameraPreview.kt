package com.shiwu.scan3d.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.suspendCancellableCoroutine

private const val TAG = "CameraPreview"
private const val ANALYZE_INTERVAL_MS = 0L
private const val SEGMENT_MAX_SIDE = 1280

@SuppressLint("MissingPermission")
@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    segmenter: SubjectSegmenter? = null,
    detectionEnabled: Boolean = false,
    onSubject: (SubjectBox?) -> Unit = {},
    onAspect: (Float) -> Unit = {},
    onImageCaptureReady: (ImageCapture) -> Unit,
    onCameraBound: (androidx.camera.core.Camera) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    // 预览和分析统一用 4:3，叠加层的框才能和看到的画面严格对齐。
    val analysisResolution = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .setResolutionStrategy(
                ResolutionStrategy(
                    Size(640, 480),
                    ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER
                )
            )
            .build()
    }
    val previewResolution = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageAnalysis = remember {
        ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .setResolutionSelector(analysisResolution)
            .build()
    }
    val analyzerExecutor = remember {
        Executors.newSingleThreadExecutor { Thread(it, "shiwu-subject") }
    }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    val enabledNow = rememberUpdatedState(detectionEnabled)
    val segmenterNow = rememberUpdatedState(segmenter)
    val subjectNow = rememberUpdatedState(onSubject)
    val aspectNow = rememberUpdatedState(onAspect)

    SideEffect { onImageCaptureReady(imageCapture) }

    AndroidView(factory = { previewView }, modifier = modifier)

    DisposableEffect(Unit) {
        var lastRun = 0L
        var loggedStart = false
        imageAnalysis.setAnalyzer(analyzerExecutor) { proxy ->
            try {
                val subjectSegmenter = segmenterNow.value
                if (!loggedStart) {
                    loggedStart = true
                    Log.i(
                        TAG,
                        "analysis ${proxy.width}x${proxy.height} " +
                                "rot=${proxy.imageInfo.rotationDegrees} enabled=${enabledNow.value}"
                    )
                }
                if (!enabledNow.value || subjectSegmenter == null) return@setAnalyzer
                val now = SystemClock.elapsedRealtime()
                if (now - lastRun < ANALYZE_INTERVAL_MS) return@setAnalyzer
                lastRun = now

                val upright = proxy.uprightBitmap()
                val scaled = upright.scaledToSide(SEGMENT_MAX_SIDE)
                if (scaled !== upright) upright.recycle()

                val aspect = scaled.width.toFloat() / scaled.height.toFloat()
                val box = try {
                    subjectSegmenter.analyze(scaled)
                } finally {
                    scaled.recycle()
                }
                mainHandler.post {
                    aspectNow.value(aspect)
                    subjectNow.value(box)
                }
            } catch (t: Throwable) {
                Log.w(TAG, "analysis failed", t)
                mainHandler.post { subjectNow.value(null) }
            } finally {
                proxy.close()
            }
        }
        onDispose {
            imageAnalysis.clearAnalyzer()
            analyzerExecutor.shutdownNow()
        }
    }

    LaunchedEffect(Unit) {
        val provider = ProcessCameraProvider.getInstance(context).awaitProvider(context)
        val preview = Preview.Builder()
            .setResolutionSelector(previewResolution)
            .build()
            .apply { setSurfaceProvider(previewView.surfaceProvider) }
        runCatching {
            provider.unbindAll()
            val camera = provider.bindToLifecycle(
                lifecycleOwner,
                CameraSelector.DEFAULT_BACK_CAMERA,
                preview,
                imageCapture,
                imageAnalysis
            )
            onCameraBound(camera)
        }.onFailure { error ->
            // 低端机可能凑不齐三个用例，退化为只预览 + 拍照，主体框自动隐藏。
            Log.w(TAG, "bind with analysis failed, falling back", error)
            runCatching {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageCapture
                )
                onCameraBound(camera)
                mainHandler.post { onSubject(null) }
            }.onFailure { Log.e(TAG, "camera bind failed", it) }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                ProcessCameraProvider.getInstance(context).get().unbindAll()
            }
        }
    }
}

private suspend fun ListenableFuture<ProcessCameraProvider>.awaitProvider(
    context: Context
): ProcessCameraProvider = suspendCancellableCoroutine { cont ->
    addListener({
        runCatching { get() }
            .onSuccess { cont.resume(it) }
            .onFailure { cont.resumeWith(Result.failure(it)) }
    }, ContextCompat.getMainExecutor(context))
}

/** 按 sensor 方向摆正，分析结果就能直接按屏幕方向叠加。 */
private fun ImageProxy.uprightBitmap(): Bitmap {
    val bitmap = toBitmap()
    val degrees = imageInfo.rotationDegrees
    if (degrees == 0) return bitmap
    val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
    val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (rotated !== bitmap) bitmap.recycle()
    return rotated
}

private fun Bitmap.scaledToSide(side: Int): Bitmap {
    val longest = maxOf(width, height)
    if (longest == side) return this
    val ratio = side.toFloat() / longest
    return Bitmap.createScaledBitmap(
        this,
        (width * ratio).roundToInt().coerceAtLeast(1),
        (height * ratio).roundToInt().coerceAtLeast(1),
        true
    )
}
