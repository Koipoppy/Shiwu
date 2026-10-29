package com.shiwu.scan3d.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shiwu.scan3d.data.Engine
import com.shiwu.scan3d.data.Quality
import com.shiwu.scan3d.data.ScanPhoto
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.Pill
import com.shiwu.scan3d.ui.components.ScreenHeader
import com.shiwu.scan3d.ui.components.SectionTitle
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.InkSoft
import com.shiwu.scan3d.ui.theme.Line
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint50
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.Sand
import com.shiwu.scan3d.ui.theme.White
import java.io.File

@Composable
fun ReviewScreen(vm: AppViewModel, state: AppState) {
    val single = state.singleShot
    AmbientBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            ScreenHeader(
                title = "检查素材",
                onBack = { vm.back() },
                trailing = {
                    Pill(
                        text = if (single) "单图模式" else "${state.photos.size} 张",
                        background = Mint100,
                        foreground = Mint700
                    )
                }
            )

            Box(Modifier.weight(1f)) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(bottom = 14.dp)
                ) {
                    Text(
                        if (single) "确认这张照片能看清物体" else "确认这些照片能看清物体",
                        style = MaterialTheme.typography.bodyMedium,
                        color = InkSoft,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                Spacer(Modifier.height(14.dp))

                Column(
                    Modifier.padding(horizontal = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.photos.chunked(3).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            row.forEach { photo ->
                                PhotoTile(
                                    photo = photo,
                                    modifier = Modifier.weight(1f),
                                    onRemove = { vm.removePhoto(photo.path) }
                                )
                            }
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }

                    if (!single && state.photos.size < 12) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            AddSourceChip(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.CameraAlt,
                                label = "继续拍摄",
                                onClick = { vm.resumeCapture() }
                            )
                            AddSourceChip(
                                modifier = Modifier.weight(1f),
                                icon = Icons.Rounded.PhotoLibrary,
                                label = "相册添加",
                                onClick = { vm.go(com.shiwu.scan3d.ui.Screen.PickPhotos) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                TipsCard(
                    single = single,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(24.dp))

                SectionTitle("作品名称")
                OutlinedTextField(
                    value = state.name,
                    onValueChange = vm::setName,
                    singleLine = true,
                    placeholder = { Text("给这次扫描起个名字", color = InkFaint) },
                    shape = RoundedCornerShape(16.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mint500,
                        unfocusedBorderColor = Line,
                        focusedContainerColor = White,
                        unfocusedContainerColor = White,
                        cursorColor = Mint700
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                )

                Spacer(Modifier.height(20.dp))

                SectionTitle(
                    "生成设置",
                    trailing = {
                        Text(
                            "${state.engine.label} · ${state.quality.hint}",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                    }
                )
                Column(Modifier.padding(horizontal = 20.dp)) {
                    OptionRow(label = "引擎") {
                        Engine.entries.forEach { engine ->
                            CompactOption(
                                text = engine.label,
                                selected = state.engine == engine
                            ) { vm.setEngine(engine) }
                        }
                    }
                    Spacer(Modifier.height(9.dp))
                    OptionRow(label = "精度") {
                        Quality.entries.forEach { quality ->
                            CompactOption(
                                text = quality.label,
                                selected = state.quality == quality
                            ) { vm.setQuality(quality) }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                }

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                            )
                        )
                )
            }

            SurfaceCta(
                enabled = state.photos.isNotEmpty(),
                demo = state.settings.demoMode,
                onClick = { vm.startGeneration() }
            )
        }
    }
}

@Composable
private fun PhotoTile(photo: ScanPhoto, modifier: Modifier = Modifier, onRemove: () -> Unit) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(Mint50)
    ) {
        AsyncImage(
            model = File(photo.path),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(6.dp)
                .size(24.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .clickable(onClick = onRemove),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "移除", tint = White, modifier = Modifier.size(14.dp))
        }
        if (photo.label.isNotBlank()) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    photo.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = White,
                    fontSize = 9.sp
                )
            }
        }
    }
}

@Composable
private fun AddSourceChip(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(White.copy(alpha = 0.7f))
            .border(1.dp, Line, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, contentDescription = null, tint = Mint700, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, color = InkSoft)
    }
}

@Composable
private fun TipsCard(single: Boolean, modifier: Modifier = Modifier) {
    val title = if (single) "单图也能用，但要知道边界" else "拍得更好，模型更准"
    val tips = if (single) {
        listOf(
            "主体完整、居中，别被手指挡住",
            "背景干净，避免强反光和高光",
            "背面等看不见的部分由 AI 推断",
            "想要更准，改用环绕扫描"
        )
    } else {
        listOf(
            "主体完整入镜，别被手指挡住",
            "背景干净、光线均匀，不要开闪光灯",
            "相邻两张转角保持 30°–45°",
            "至少 4 张，8–12 张效果最好"
        )
    }
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(Sand.copy(alpha = 0.35f))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Lightbulb,
                contentDescription = null,
                tint = Color(0xFF8A6A32),
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = Color(0xFF6B5122)
            )
        }
        Spacer(Modifier.height(8.dp))
        tips.forEach { tip ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text("· ", style = MaterialTheme.typography.bodySmall, color = Color(0xFF8A6A32))
                Text(tip, style = MaterialTheme.typography.bodySmall, color = Color(0xFF6B5122))
            }
        }
    }
}

@Composable
private fun OptionRow(label: String, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = InkFaint,
            modifier = Modifier.width(34.dp)
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun CompactOption(text: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        Modifier
            .clip(shape)
            .background(if (selected) Mint700 else White)
            .border(1.dp, if (selected) Mint700 else Line, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) White else InkSoft,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SurfaceCta(enabled: Boolean, demo: Boolean, onClick: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            )
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 12.dp)
    ) {
        Button(
            onClick = onClick,
            enabled = enabled,
            shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Mint700,
                contentColor = White,
                disabledContainerColor = Mint100,
                disabledContentColor = Mint700.copy(alpha = 0.5f)
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (demo) "开始生成（演示）" else "开始生成",
                style = MaterialTheme.typography.labelLarge
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (demo) "演示模式不会真的调用接口" else "预计 1–3 分钟 · 会消耗 1 次生成额度",
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.fillMaxWidth(),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
