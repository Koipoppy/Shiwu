package com.shiwu.scan3d.ui.device

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.shiwu.scan3d.data.Gallery
import com.shiwu.scan3d.data.GalleryItem
import com.shiwu.scan3d.ui.components.CameraPreview
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.AmberDim
import com.shiwu.scan3d.ui.theme.Brass
import com.shiwu.scan3d.ui.theme.BrassDark
import com.shiwu.scan3d.ui.theme.Cream
import com.shiwu.scan3d.ui.theme.Deck
import com.shiwu.scan3d.ui.theme.DeckEdge
import com.shiwu.scan3d.ui.theme.Glass
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.ShellBottom
import com.shiwu.scan3d.ui.theme.ShellTop
import com.shiwu.scan3d.ui.theme.glossy
import com.shiwu.scan3d.ui.viewer.ModelViewer
import com.shiwu.scan3d.util.shareModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DeviceScreen(vm: DeviceViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var hasCamera by remember {
        mutableStateOf(
            androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                    PackageManager.PERMISSION_GRANTED
        )
    }
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { hasCamera = it }

    LaunchedEffect(Unit) {
        if (!hasCamera) cameraLauncher.launch(Manifest.permission.CAMERA)
    }

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var torchOn by remember { mutableStateOf(false) }
    var shooting by remember { mutableStateOf(false) }
    val flash = remember { Animatable(0f) }

    var showKeyDialog by remember { mutableStateOf(false) }

    var mediaGranted by remember { mutableStateOf(Gallery.hasPermission(context)) }
    val mediaLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { mediaGranted = it }
    var importItems by remember { mutableStateOf<List<GalleryItem>>(emptyList()) }
    var importSelected by remember { mutableStateOf<List<Long>>(emptyList()) }
    var importLoading by remember { mutableStateOf(false) }

    LaunchedEffect(state.mode, mediaGranted) {
        if (state.mode == DeviceMode.IMPORT) {
            if (!mediaGranted) {
                mediaLauncher.launch(Gallery.permission())
            } else {
                importLoading = true
                importItems = withContext(Dispatchers.IO) { Gallery.query(context) }
                importLoading = false
            }
        } else {
            importSelected = emptyList()
        }
    }

    LaunchedEffect(state.toast) {
        val msg = state.toast
        if (msg != null) {
            snackbar.showSnackbar(msg)
            vm.clearToast()
        }
    }

    BackHandler(enabled = state.mode != DeviceMode.LIVE) { vm.toLive() }

    fun shoot() {
        val capture = imageCapture
        if (!hasCamera) {
            cameraLauncher.launch(Manifest.permission.CAMERA)
            return
        }
        if (capture == null || shooting) return
        if (state.frames.size >= state.maxFrames) {
            vm.toast("底片已满 12 帧")
            return
        }
        shooting = true
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            flash.snapTo(1f)
            flash.animateTo(0f, tween(260))
        }
        val dir = File(context.filesDir, "scans/${state.sessionId.ifBlank { "default" }}").apply { mkdirs() }
        val file = File(dir, "frame_${System.currentTimeMillis()}.jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(
            options,
            androidx.core.content.ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                    shooting = false
                    vm.capture(file.absolutePath)
                }

                override fun onError(exception: ImageCaptureException) {
                    shooting = false
                    vm.toast("拍摄失败：${exception.message}")
                }
            }
        )
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(ShellTop, ShellBottom, ShellBottom)))
    ) {
        val landscape = maxWidth > maxHeight
        val hudRotation = rememberHudRotation()

        val viewfinder: @Composable (Modifier, Boolean) -> Unit = { mod, compact ->
            CrtScreen(
                modifier = mod,
                hudLeft = if (state.mode == DeviceMode.LIVE) "F2.8 ISO400 24MM" else "",
                hudRight = "%03d/%02d".format(state.frames.size, state.maxFrames),
                hudBottom = when (state.mode) {
                    DeviceMode.LIVE -> if (state.frames.isEmpty()) "ALIGN OBJECT / TAP TO CAPTURE" else "TAP = FRAME  HOLD = SUBMIT"
                    DeviceMode.CONFIG -> "PARAMETERS"
                    DeviceMode.ARCHIVE -> "LOCAL ARCHIVE"
                    DeviceMode.IMPORT -> "ALBUM INPUT"
                    DeviceMode.PREVIEW -> "3D PREVIEW"
                    DeviceMode.PROCESSING -> "PROCESSING"
                },
                recording = state.frames.isNotEmpty() && state.mode == DeviceMode.LIVE,
                compact = compact,
                flash = flash.value,
                hudRotation = hudRotation
            ) {
                if (hasCamera) {
                    CameraPreview(
                        modifier = Modifier.fillMaxSize(),
                        onImageCaptureReady = { imageCapture = it },
                        onCameraBound = { camera = it }
                    )
                } else {
                    CameraBlockedPanel(onGrant = { cameraLauncher.launch(Manifest.permission.CAMERA) })
                }

                AnimatedContent(
                    targetState = state.mode,
                    transitionSpec = {
                        (fadeIn(tween(180)) + scaleIn(tween(200), initialScale = 0.96f))
                            .togetherWith(fadeOut(tween(120)) + scaleOut(tween(160), targetScale = 1.02f))
                    },
                    label = "panel"
                ) { mode ->
                    when (mode) {
                        DeviceMode.LIVE -> Box(Modifier.fillMaxSize())
                        DeviceMode.CONFIG -> PanelSurface {
                            ConfigPanel(
                                engineLabel = state.engine.label,
                                qualityLabel = state.quality.label,
                                regionLabel = if (state.settings.globalRegion) "GLOBAL" else "CN",
                                demoMode = state.settings.demoMode,
                                hasKey = state.settings.apiKey.isNotBlank(),
                                frames = state.frames.size,
                                probeText = when {
                                    state.probe.checking -> "…"
                                    state.probe.message.isBlank() -> "TEST"
                                    else -> state.probe.message
                                },
                                probeOk = state.probe.ok,
                                onCycleEngine = vm::cycleEngine,
                                onCycleQuality = vm::cycleQuality,
                                onToggleRegion = vm::toggleRegion,
                                onToggleDemo = vm::toggleDemo,
                                onEditKey = { showKeyDialog = true },
                                onTest = vm::testConnection,
                                onOpenImport = vm::openImport,
                                onClearFrames = vm::clearFrames
                            )
                        }
                        DeviceMode.ARCHIVE -> PanelSurface {
                            ArchivePanel(
                                assets = state.assets,
                                onOpen = { vm.openPreview(it.id) }
                            )
                        }
                        DeviceMode.IMPORT -> PanelSurface {
                            ImportPanel(
                                items = importItems,
                                selected = importSelected,
                                loading = importLoading,
                                onToggle = { id ->
                                    importSelected = if (importSelected.contains(id)) {
                                        importSelected - id
                                    } else if (importSelected.size >= state.maxFrames - state.frames.size) {
                                        importSelected
                                    } else {
                                        importSelected + id
                                    }
                                },
                                onConfirm = {
                                    val uris = importSelected.mapNotNull { id ->
                                        importItems.firstOrNull { it.id == id }?.uri
                                    }
                                    vm.importFrames(uris)
                                },
                                onClose = vm::toLive
                            )
                        }
                        DeviceMode.PREVIEW -> state.previewAsset?.let { asset ->
                            Box(Modifier.fillMaxSize()) {
                                ModelViewer(
                                    modelFile = File(asset.modelPath),
                                    modifier = Modifier.fillMaxSize(),
                                    autoRotate = true,
                                    dark = true
                                )
                                PreviewOverlay(
                                    asset = asset,
                                    onClose = vm::toLive,
                                    onShare = { shareModel(context, File(asset.modelPath)) },
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(
                                            start = 14.dp,
                                            end = 14.dp,
                                            top = if (compact) 36.dp else 44.dp,
                                            bottom = if (compact) 24.dp else 30.dp
                                        )
                                )
                            }
                        } ?: Box(Modifier.fillMaxSize())
                        DeviceMode.PROCESSING -> PanelSurface {
                            ProcessingPanel(
                                job = state.job ?: com.shiwu.scan3d.data.JobUi(),
                                onCancel = vm::cancelGeneration,
                                onRetry = vm::retryGeneration
                            )
                        }
                    }
                }
            }
        }

        val subDisplay: @Composable (Boolean) -> Unit = { compact ->
            SubDisplay(
                state = state,
                torchOn = torchOn,
                compact = compact,
                onToggleTorch = {
                    camera?.let { c ->
                        torchOn = !torchOn
                        runCatching { c.cameraControl.enableTorch(torchOn) }
                    }
                }
            )
        }

        val deck: @Composable (Boolean, Boolean) -> Unit = { vertical, compact ->
            ControlDeck(
                state = state,
                vertical = vertical,
                compact = compact,
                onShoot = { shoot() },
                onSubmit = { vm.submit() },
                onConfig = vm::openConfig,
                onArchive = vm::openArchive
            )
        }

        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            subDisplay(landscape)
            Spacer(Modifier.height(10.dp))
            viewfinder(Modifier.weight(1f).fillMaxWidth(), landscape)
            Spacer(Modifier.height(10.dp))
            deck(false, landscape)
            Spacer(Modifier.height(14.dp))
        }

        SnackbarHost(
            hostState = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp, start = 16.dp, end = 16.dp)
        )
    }

    if (showKeyDialog) {
        ApiKeyDialog(
            initial = state.settings.apiKey,
            onDismiss = { showKeyDialog = false },
            onSave = { key ->
                vm.updateSettings(state.settings.copy(apiKey = key.trim()))
                showKeyDialog = false
            }
        )
    }
}

/**
 * 顶部子显示屏：帧数、进度、引擎/精度、手电筒与操作提示。
 */
@Composable
private fun SubDisplay(
    state: DeviceState,
    torchOn: Boolean,
    compact: Boolean,
    onToggleTorch: () -> Unit
) {
    val live = state.mode == DeviceMode.LIVE
    val hint = when (state.mode) {
        DeviceMode.LIVE -> if (state.frames.isEmpty()) "轻按拍摄 · 长按提交" else "轻按继续拍 · 长按现在提交"
        DeviceMode.CONFIG, DeviceMode.ARCHIVE, DeviceMode.IMPORT -> "再按一次对应按钮返回取景"
        DeviceMode.PREVIEW -> "拖动旋转 · 双指缩放"
        DeviceMode.PROCESSING -> "成像中，请稍候"
    }

    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(3.dp))
            .background(Color(0xFF0B0D0A))
            .border(1.dp, BrassDark.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
            .padding(horizontal = 10.dp, vertical = if (compact) 5.dp else 7.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "FRAMES",
                style = MaterialTheme.typography.labelSmall,
                color = AmberDim,
                fontSize = 8.sp
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "%03d".format(state.frames.size),
                style = MaterialTheme.typography.titleMedium.glossy(),
                color = Amber,
                fontSize = if (compact) 12.sp else 16.sp
            )
            Spacer(Modifier.width(10.dp))
            Row(
                Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(state.maxFrames) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(4.dp)
                            .background(
                                if (i < state.frames.size) Amber else Cream.copy(alpha = 0.10f),
                                RoundedCornerShape(1.dp)
                            )
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                "${state.engine.label.uppercase()} / ${state.quality.label}",
                style = MaterialTheme.typography.labelSmall,
                color = Cream.copy(alpha = 0.6f),
                fontSize = 8.sp,
                maxLines = 1
            )
        }

        Spacer(Modifier.height(5.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(2.dp))
                    .border(
                        1.dp,
                        if (torchOn) Amber else Brass.copy(alpha = 0.35f),
                        RoundedCornerShape(2.dp)
                    )
                    .clickable(enabled = live, onClick = onToggleTorch)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (torchOn) "灯 ON" else "灯 OFF",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (torchOn) Amber else Cream.copy(alpha = 0.45f),
                    fontSize = 8.sp
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                hint,
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.55f),
                fontSize = if (compact) 8.sp else 12.sp,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PanelSurface(content: @Composable () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Glass.copy(alpha = 0.985f))
    ) { content() }
}

@Composable
private fun CameraBlockedPanel(onGrant: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xFF0A0D0A)),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "CAMERA BLOCKED",
                style = MaterialTheme.typography.titleSmall.glossy(),
                color = Amber
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "需要相机权限才能取景",
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.6f)
            )
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .clip(RoundedCornerShape(3.dp))
                    .border(1.dp, Brass.copy(alpha = 0.6f), RoundedCornerShape(3.dp))
                    .clickable(onClick = onGrant)
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    "GRANT",
                    style = MaterialTheme.typography.labelLarge.glossy(),
                    color = Amber
                )
            }
        }
    }
}

@Composable
private fun ControlDeck(
    state: DeviceState,
    vertical: Boolean,
    compact: Boolean,
    onShoot: () -> Unit,
    onSubmit: () -> Unit,
    onConfig: () -> Unit,
    onArchive: () -> Unit
) {
    val live = state.mode == DeviceMode.LIVE
    val knobSize = if (compact) 100.dp else 116.dp
    val buttonSize = if (compact) 54.dp else 66.dp

    val config: @Composable () -> Unit = {
        HardwareButton(
            label = "配置",
            sub = "CONFIG",
            active = state.mode == DeviceMode.CONFIG,
            size = buttonSize,
            onClick = onConfig
        ) { color -> drawSliders(color) }
    }
    val archive: @Composable () -> Unit = {
        HardwareButton(
            label = "资产",
            sub = "ARCHIVE",
            active = state.mode == DeviceMode.ARCHIVE,
            size = buttonSize,
            onClick = onArchive
        ) { color -> drawArchive(color) }
    }
    val knob: @Composable () -> Unit = {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ShutterKnob(
                turns = state.frames.size,
                frames = state.frames.size,
                maxFrames = state.maxFrames,
                enabled = live,
                diameter = knobSize,
                onTap = onShoot,
                onLongPress = onSubmit
            )
            Spacer(Modifier.height(2.dp))
            Text(
                if (state.frames.isEmpty()) "SHUTTER" else "长按提交 · ${state.frames.size} 帧",
                style = MaterialTheme.typography.labelSmall,
                color = if (live) AmberDim else Cream.copy(alpha = 0.3f),
                fontSize = 8.sp
            )
        }
    }

    if (vertical) {
        Column(
            Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            config()
            knob()
            archive()
        }
    } else {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            config()
            knob()
            archive()
        }
    }
}

@Composable
private fun HardwareButton(
    label: String,
    sub: String,
    active: Boolean,
    size: Dp,
    onClick: () -> Unit,
    glyph: DrawScope.(Color) -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(Color(0xFF3B3428), Color(0xFF221D16))
                    )
                )
                .border(1.dp, if (active) Amber else DeckEdge, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Canvas(Modifier.size(size * 0.4f)) {
                glyph(if (active) Amber else Cream.copy(alpha = 0.62f))
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (active) Amber else Cream.copy(alpha = 0.6f),
            fontSize = 8.sp
        )
        Text(
            sub,
            style = MaterialTheme.typography.labelSmall,
            color = Cream.copy(alpha = 0.28f),
            fontSize = 8.sp
        )
    }
}

private fun DrawScope.drawSliders(color: Color) {
    val w = size.width
    val h = size.height
    val ys = listOf(h * 0.22f, h * 0.5f, h * 0.78f)
    val knobX = listOf(w * 0.34f, w * 0.68f, w * 0.46f)
    ys.forEachIndexed { i, y ->
        drawLine(color.copy(alpha = 0.75f), Offset(0f, y), Offset(w, y), 1.6.dp.toPx(), StrokeCap.Round)
        drawCircle(color, 2.6.dp.toPx(), Offset(knobX[i], y))
    }
}

private fun DrawScope.drawArchive(color: Color) {
    val w = size.width
    val h = size.height
    for (i in 0..2) {
        val inset = i * 1.6.dp.toPx()
        drawRoundRect(
            color = color.copy(alpha = 0.35f + i * 0.2f),
            topLeft = Offset(inset, h * 0.18f - inset),
            size = Size(w - inset * 2, h * 0.64f + inset * 2),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.5.dp.toPx())
        )
    }
}

@Composable
private fun ApiKeyDialog(initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(4.dp))
                .background(Deck)
                .border(2.dp, Brass.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(14.dp)
        ) {
            Text(
                "API KEY",
                style = MaterialTheme.typography.titleSmall.glossy(),
                color = Amber
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "在 lux3d.aholo3d.cn 控制台申请，仅存本机",
                style = MaterialTheme.typography.labelSmall,
                color = Cream.copy(alpha = 0.5f),
                fontSize = 8.sp
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodySmall,
                shape = RoundedCornerShape(2.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Amber,
                    unfocusedBorderColor = Brass.copy(alpha = 0.4f),
                    focusedTextColor = Cream,
                    unfocusedTextColor = Cream,
                    cursorColor = Amber,
                    focusedContainerColor = Color(0xFF14110C),
                    unfocusedContainerColor = Color(0xFF14110C)
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelAction("取消", onClick = onDismiss, modifier = Modifier.weight(1f))
                PanelAction("写入", onClick = { onSave(value) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

/**
 * 机身姿态：返回 HUD 需要旋转的角度（0 / 90 / 180 / 270）。
 * 应用锁竖屏，屏幕内容不动，只有取景 HUD 跟着机身转，保证用户看到的方向是正的。
 */
@Composable
private fun rememberHudRotation(): Int {
    val context = LocalContext.current
    var angle by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
        val sensor = sm?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var smoothX = 0f
        var smoothY = 9.81f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return
                val ax = event.values[0]
                val ay = event.values[1]
                // 平放在桌上时重力几乎全在 Z 轴，平面分量很小，角度不可靠：
                // 此时保持上一次方向，避免噪声把 HUD 甩到 90°。
                if (Math.hypot(ax.toDouble(), ay.toDouble()) < 2.5) return
                smoothX = smoothX * 0.82f + ax * 0.18f
                smoothY = smoothY * 0.82f + ay * 0.18f
                val deg = Math.toDegrees(
                    kotlin.math.atan2(smoothX.toDouble(), smoothY.toDouble())
                ).toFloat()
                val snapped = (Math.round(deg / 90f) * 90).toInt()
                if (snapped != angle) angle = snapped
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        if (sm != null && sensor != null) {
            sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        }
        onDispose {
            if (sm != null) sm.unregisterListener(listener)
        }
    }
    return angle
}
