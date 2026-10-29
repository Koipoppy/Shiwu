package com.shiwu.scan3d.ui.screens

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import coil.compose.AsyncImage
import com.shiwu.scan3d.data.ScanPhoto
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.components.CameraPreview
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint300
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White
import java.io.File
import kotlinx.coroutines.launch

private val ANGLE_LABELS = listOf("正面", "右侧 45°", "右侧 90°", "右后 135°", "背面", "左后 135°", "左侧 90°", "左侧 45°")

@Composable
fun CaptureScreen(vm: AppViewModel, state: AppState) {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val view = LocalView.current

    // 取景页是深色背景，状态栏图标改成浅色
    DisposableEffect(Unit) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose {
            if (previous != null) controller.isAppearanceLightStatusBars = previous
        }
    }

    val maxShots = if (state.singleShot) 1 else 12
    val minShots = if (state.singleShot) 1 else 4

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<androidx.camera.core.Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }

    val photos = state.photos
    val full = photos.size >= maxShots

    fun shoot() {
        val capture = imageCapture ?: return
        if (busy || full) return
        busy = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            flash.snapTo(0.85f)
            flash.animateTo(0f, tween(260))
        }
        val dir = File(context.filesDir, "scans/${state.sessionId}").apply { mkdirs() }
        val file = File(dir, "shot_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    busy = false
                    vm.addPhoto(file)
                    if (state.singleShot) vm.go(com.shiwu.scan3d.ui.Screen.Review)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    vm.showToast("拍照失败：${exception.message}")
                }
            }
        )
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF0E1210))
    ) {
        if (hasPermission) {
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                onImageCaptureReady = { imageCapture = it },
                onCameraBound = { camera = it }
            )
        } else {
            PermissionGate(onRequest = { permissionLauncher.launch(Manifest.permission.CAMERA) })
        }

        // 上下压暗，保证文字可读
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.55f),
                        0.28f to Color.Transparent,
                        0.62f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.82f)
                    )
                )
        )

        if (hasPermission) {
            ScanGuideOverlay(shotCount = photos.size, maxShots = maxShots)
        }

        // 快门闪光
        Box(
            Modifier
                .fillMaxSize()
                .background(White.copy(alpha = flash.value * 0.55f))
        )

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp)
        ) {
            CaptureTopBar(
                photos = photos,
                maxShots = maxShots,
                minShots = minShots,
                onClose = { vm.go(com.shiwu.scan3d.ui.Screen.Home) },
                onDone = { vm.go(com.shiwu.scan3d.ui.Screen.Review) }
            )

            Spacer(Modifier.weight(1f))

            AnimatedVisibility(
                visible = !state.singleShot && photos.size >= 8 && !full,
                enter = fadeIn() + slideInVertically { it / 3 },
                exit = fadeOut()
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(White.copy(alpha = 0.95f))
                        .clickable { vm.go(com.shiwu.scan3d.ui.Screen.Review) }
                        .padding(horizontal = 16.dp, vertical = 13.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(Mint100),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = Mint700,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            "素材已经够用了",
                            style = androidx.compose.material3.MaterialTheme.typography.titleSmall,
                            color = com.shiwu.scan3d.ui.theme.Ink
                        )
                        Text(
                            "8 张就够，想更稳可以再补角度",
                            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
                            color = com.shiwu.scan3d.ui.theme.InkFaint
                        )
                    }
                    Icon(
                        Icons.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = Mint700,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (!state.singleShot && photos.size >= 8 && !full) {
                Spacer(Modifier.height(10.dp))
            }

            Text(
                text = when {
                    state.singleShot ->
                        if (photos.isEmpty()) "对准物体，拍一张清晰的照片" else "拍好了，点右上角「完成」"
                    photos.isEmpty() -> "把物体放在圆环中间，从正面开始"
                    full -> "拍满了，点右上角「完成」检查素材"
                    photos.size >= 8 -> "继续补角度也行，但现在已经可以提交"
                    else -> "下一张：${ANGLE_LABELS.getOrElse(photos.size) { "继续转一点" }}"
                },
                style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
                color = White.copy(alpha = 0.92f),
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            Spacer(Modifier.height(18.dp))

            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // 最近一张
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(White.copy(alpha = 0.14f))
                        .clickable(enabled = photos.isNotEmpty()) {
                            vm.go(com.shiwu.scan3d.ui.Screen.Review)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    val last = photos.lastOrNull()
                    if (last != null) {
                        AsyncImage(
                            model = File(last.path),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            tint = White.copy(alpha = 0.6f),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                ShutterButton(
                    busy = busy,
                    enabled = hasPermission && !full,
                    count = photos.size,
                    max = maxShots,
                    onClick = { shoot() }
                )

                // 手电筒
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (torchOn) White else White.copy(alpha = 0.14f))
                        .clickable(enabled = hasPermission) {
                            val c = camera ?: return@clickable
                            torchOn = !torchOn
                            runCatching { c.cameraControl.enableTorch(torchOn) }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (torchOn) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                        contentDescription = "手电筒",
                        tint = if (torchOn) Color(0xFF0E1210) else White.copy(alpha = 0.85f),
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            Spacer(Modifier.height(22.dp))
        }
    }
}

@Composable
private fun CaptureTopBar(
    photos: List<ScanPhoto>,
    maxShots: Int,
    minShots: Int,
    onClose: () -> Unit,
    onDone: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "关闭", tint = White, modifier = Modifier.size(20.dp))
        }

        Spacer(Modifier.weight(1f))

        Row(
            Modifier
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f))
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(Mint300)
            )
            Spacer(Modifier.width(7.dp))
            Text(
                "${photos.size} / $maxShots",
                style = androidx.compose.material3.MaterialTheme.typography.labelMedium,
                color = White
            )
        }

        Spacer(Modifier.weight(1f))

        val canDone = photos.size >= minShots
        Text(
            "完成",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = if (canDone) White else White.copy(alpha = 0.35f),
            modifier = Modifier
                .clip(CircleShape)
                .clickable(enabled = canDone, onClick = onDone)
                .background(if (canDone) Mint500 else Color.Transparent)
                .padding(horizontal = 16.dp, vertical = 9.dp)
        )
    }
}

@Composable
private fun ShutterButton(
    busy: Boolean,
    enabled: Boolean,
    count: Int,
    max: Int,
    onClick: () -> Unit
) {
    Box(
        Modifier
            .size(92.dp)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 4.dp.toPx()
            val inset = stroke / 2
            val arcSize = androidx.compose.ui.geometry.Size(size.width - stroke, size.height - stroke)
            drawArc(
                color = White.copy(alpha = 0.22f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            val fraction = (count.toFloat() / max).coerceIn(0f, 1f)
            if (fraction > 0f) {
                drawArc(
                    color = Mint300,
                    startAngle = -90f,
                    sweepAngle = 360f * fraction,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round)
                )
            }
        }
        Box(
            Modifier
                .size(66.dp)
                .clip(CircleShape)
                .background(if (enabled) White else White.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            if (busy) {
                CircularProgressIndicator(
                    color = Mint500,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(24.dp)
                )
            } else {
                Box(
                    Modifier
                        .size(78.dp)
                        .clip(CircleShape)
                        .background(Color.Transparent)
                )
            }
        }
    }
}

@Composable
private fun ScanGuideOverlay(shotCount: Int, maxShots: Int) {
    Canvas(Modifier.fillMaxSize()) {
        val center = Offset(size.width / 2f, size.height * 0.42f)
        val radius = size.minDimension * 0.31f

        drawCircle(
            color = White.copy(alpha = 0.55f),
            radius = radius,
            center = center,
            style = Stroke(
                width = 2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 12f))
            )
        )

        // 四角括号
        val half = radius * 1.12f
        val leg = radius * 0.22f
        val color = White.copy(alpha = 0.75f)
        val w = 3.dp.toPx()
        val corners = listOf(
            Offset(center.x - half, center.y - half) to listOf(1f to 0f, 0f to 1f),
            Offset(center.x + half, center.y - half) to listOf(-1f to 0f, 0f to 1f),
            Offset(center.x - half, center.y + half) to listOf(1f to 0f, 0f to -1f),
            Offset(center.x + half, center.y + half) to listOf(-1f to 0f, 0f to -1f)
        )
        corners.forEach { (o, dirs) ->
            dirs.forEach { (dx, dy) ->
                drawLine(
                    color = color,
                    start = o,
                    end = Offset(o.x + leg * dx, o.y + leg * dy),
                    strokeWidth = w,
                    cap = StrokeCap.Round
                )
            }
        }

        // 已拍数量用小刻度环表示
        if (maxShots > 1) {
            val tickCount = maxShots
            for (i in 0 until tickCount) {
                val angle = Math.toRadians((i * 360.0 / tickCount) - 90.0)
                val r1 = radius * 1.18f
                val r2 = radius * (if (i < shotCount) 1.26f else 1.22f)
                drawLine(
                    color = if (i < shotCount) Mint300 else White.copy(alpha = 0.25f),
                    start = Offset(
                        center.x + (r1 * Math.cos(angle)).toFloat(),
                        center.y + (r1 * Math.sin(angle)).toFloat()
                    ),
                    end = Offset(
                        center.x + (r2 * Math.cos(angle)).toFloat(),
                        center.y + (r2 * Math.sin(angle)).toFloat()
                    ),
                    strokeWidth = if (i < shotCount) 3.dp.toPx() else 2.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

@Composable
private fun PermissionGate(onRequest: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            "需要相机权限",
            style = androidx.compose.material3.MaterialTheme.typography.headlineSmall,
            color = White
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "扫描过程只在本机取景，照片会在你确认后才上传。",
            style = androidx.compose.material3.MaterialTheme.typography.bodyMedium,
            color = White.copy(alpha = 0.7f)
        )
        Spacer(Modifier.height(20.dp))
        Text(
            "允许使用相机",
            style = androidx.compose.material3.MaterialTheme.typography.labelLarge,
            color = Color(0xFF0E1210),
            modifier = Modifier
                .clip(CircleShape)
                .background(White)
                .clickable(onClick = onRequest)
                .padding(horizontal = 22.dp, vertical = 12.dp)
        )
    }
}
