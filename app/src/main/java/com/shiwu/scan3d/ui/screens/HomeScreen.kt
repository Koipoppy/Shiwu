package com.shiwu.scan3d.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.LooksOne
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.ViewInAr
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.shiwu.scan3d.data.AssetItem
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.Screen
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.AssetCoverPlaceholder
import com.shiwu.scan3d.ui.components.LogoMark
import com.shiwu.scan3d.ui.components.OutlineCard
import com.shiwu.scan3d.ui.components.Pill
import com.shiwu.scan3d.ui.components.SectionTitle
import com.shiwu.scan3d.ui.components.StepDot
import com.shiwu.scan3d.ui.components.TagText
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.InkSoft
import com.shiwu.scan3d.ui.theme.Line
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint300
import com.shiwu.scan3d.ui.theme.Mint50
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    vm: AppViewModel,
    state: AppState,
    bottomPadding: PaddingValues,
    onNavigate: (Screen) -> Unit
) {
    AmbientBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPadding.calculateBottomPadding())
        ) {
            Spacer(Modifier.height(12.dp))
            HomeHeader(onSettings = { onNavigate(Screen.Settings) })

            Spacer(Modifier.height(26.dp))

            Column(Modifier.padding(horizontal = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Pill(
                        text = "Aholo Lux3D 驱动",
                        background = White.copy(alpha = 0.75f),
                        foreground = Mint700,
                        leading = Icons.Rounded.AutoAwesome
                    )
                    if (state.settings.demoMode) {
                        Spacer(Modifier.width(8.dp))
                        Pill(text = "演示模式", background = Color(0xFFF3E2C7), foreground = Color(0xFF7A5A28))
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    "把身边的东西\n变成 3D 资产",
                    style = MaterialTheme.typography.displayMedium,
                    color = Ink,
                    lineHeight = 46.sp
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    "环绕拍几张照片，AI 会重建几何与材质，输出可直接使用的 3D 模型。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = InkSoft,
                    lineHeight = 21.sp
                )
            }

            Spacer(Modifier.height(24.dp))

            ScanPrimaryCard(
                modifier = Modifier.padding(horizontal = 20.dp),
                onClick = { vm.startSession() }
            )

            Spacer(Modifier.height(14.dp))

            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.PhotoLibrary,
                    title = "相册导入",
                    caption = "已有照片也能用",
                    onClick = { vm.go(Screen.PickPhotos) }
                )
                QuickActionCard(
                    modifier = Modifier.weight(1f),
                    icon = Icons.Rounded.LooksOne,
                    title = "单图快拍",
                    caption = "一张照片就够",
                    onClick = { vm.startSession(singleShot = true) }
                )
            }

            Spacer(Modifier.height(28.dp))

            SectionTitle(
                "最近扫描",
                trailing = {
                    if (state.assets.isNotEmpty()) {
                        Text(
                            "全部",
                            style = MaterialTheme.typography.labelMedium,
                            color = Mint700,
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { onNavigate(Screen.Library) }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            )

            if (state.assets.isEmpty()) {
                EmptyRecentCard(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    onScan = { vm.startSession() }
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.assets.take(8), key = { it.id }) { asset ->
                        RecentAssetCard(asset) { vm.openAsset(asset.id) }
                    }
                }
            }

            Spacer(Modifier.height(28.dp))

            SectionTitle("怎么用")
            Column(
                Modifier.padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StepDot("01", "环绕拍摄", "正面、侧面、背面，8–12 张最好")
                StepDot("02", "生成资产", "Lux3D 重建几何与 PBR 材质")
                StepDot("03", "预览导出", "本机可转、可缩放，支持 GLB")
            }

            Spacer(Modifier.height(28.dp))

            Text(
                "由 群核科技 Aholo Lux3D 提供 3D 生成能力",
                style = MaterialTheme.typography.labelSmall,
                color = InkFaint,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )

            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun HomeHeader(onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LogoMark()
        Spacer(Modifier.width(10.dp))
        Column {
            Text("拾物", style = MaterialTheme.typography.titleMedium, color = Ink, fontWeight = FontWeight.Bold)
            Text("3D 扫描建模", style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
        Spacer(Modifier.weight(1f))
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(White.copy(alpha = 0.8f))
                .clickable(onClick = onSettings),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Settings, contentDescription = "设置", tint = InkSoft, modifier = Modifier.size(20.dp))
        }
    }
}

@Composable
private fun ScanPrimaryCard(modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(28.dp))
            .background(Brush.linearGradient(listOf(Mint700, Mint500), start = Offset(0f, 0f), end = Offset(900f, 600f)))
            .clickable(onClick = onClick)
    ) {
        // 装饰圆
        Box(
            Modifier
                .size(200.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        listOf(White.copy(alpha = 0.16f), Color.Transparent)
                    ),
                    CircleShape
                )
        )
        Row(
            Modifier
                .fillMaxWidth()
                .padding(22.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Box(
                    Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(White.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = White,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    "开始扫描",
                    style = MaterialTheme.typography.headlineSmall,
                    color = White,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "环绕拍 8–12 张 · 约 1 分钟出模型",
                    style = MaterialTheme.typography.bodySmall,
                    color = White.copy(alpha = 0.82f)
                )
            }
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = White, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun QuickActionCard(
    modifier: Modifier = Modifier,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    caption: String,
    onClick: () -> Unit
) {
    OutlineCard(modifier = modifier, onClick = onClick) {
        Column(Modifier.padding(16.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Mint50),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = Mint700, modifier = Modifier.size(19.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
    }
}

@Composable
private fun EmptyRecentCard(modifier: Modifier = Modifier, onScan: () -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(White.copy(alpha = 0.6f))
            .clickable(onClick = onScan)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Mint100),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.ViewInAr, contentDescription = null, tint = Mint700, modifier = Modifier.size(24.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("还没有作品", style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.height(2.dp))
            Text("扫一个放在桌上的小东西试试", style = MaterialTheme.typography.bodySmall, color = InkSoft)
        }
        Icon(Icons.Rounded.ArrowForward, contentDescription = null, tint = Mint700, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun RecentAssetCard(asset: AssetItem, onClick: () -> Unit) {
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(22.dp))
            .background(White)
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(104.dp)
                .clip(RoundedCornerShape(16.dp))
        ) {
            if (asset.coverPath != null && File(asset.coverPath).exists()) {
                AsyncImage(
                    model = File(asset.coverPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AssetCoverPlaceholder(Modifier.fillMaxSize())
            }
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(White.copy(alpha = 0.85f))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    asset.version,
                    style = MaterialTheme.typography.labelSmall,
                    color = Mint700,
                    fontSize = 9.sp
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            asset.name,
            style = MaterialTheme.typography.titleSmall,
            color = Ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(2.dp))
        Text(
            formatDate(asset.createdAt),
            style = MaterialTheme.typography.labelSmall,
            color = InkFaint,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Spacer(Modifier.height(4.dp))
    }
}

internal fun formatDate(ts: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ts))
