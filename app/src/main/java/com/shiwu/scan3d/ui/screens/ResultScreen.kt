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
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.rounded.DeleteOutline
import com.shiwu.scan3d.data.AssetItem
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.InfoRow
import com.shiwu.scan3d.ui.components.Pill
import com.shiwu.scan3d.ui.components.TagText
import com.shiwu.scan3d.ui.components.ThinDivider
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.InkSoft
import com.shiwu.scan3d.ui.theme.Line
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White
import com.shiwu.scan3d.ui.viewer.ModelViewer
import com.shiwu.scan3d.util.humanDuration
import com.shiwu.scan3d.util.humanSize
import com.shiwu.scan3d.util.saveModelToDownloads
import com.shiwu.scan3d.util.shareModel
import java.io.File

@Composable
fun ResultScreen(vm: AppViewModel, state: AppState, assetId: String) {
    val asset = state.assets.firstOrNull { it.id == assetId }
    val context = LocalContext.current
    var showDelete by remember { mutableStateOf(false) }
    var viewerFailed by remember { mutableStateOf(false) }
    var viewerLoaded by remember { mutableStateOf(false) }

    if (asset == null) {
        AmbientBackdrop {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("找不到这个作品", style = MaterialTheme.typography.titleMedium, color = Ink)
                Spacer(Modifier.height(12.dp))
                Text(
                    "返回",
                    style = MaterialTheme.typography.labelLarge,
                    color = Mint700,
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(Mint100)
                        .clickable { vm.go(com.shiwu.scan3d.ui.Screen.Library) }
                        .padding(horizontal = 18.dp, vertical = 10.dp)
                )
            }
        }
        return
    }

    AmbientBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(White)
                        .border(1.dp, Line, CircleShape)
                        .clickable { vm.go(com.shiwu.scan3d.ui.Screen.Home) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.ArrowBack,
                        contentDescription = "返回",
                        tint = Ink,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("扫描完成", style = MaterialTheme.typography.titleLarge, color = Ink)
                    Text(
                        formatDate(asset.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = InkFaint
                    )
                }
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(White)
                        .border(1.dp, Line, CircleShape)
                        .clickable { showDelete = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.MoreHoriz,
                        contentDescription = "更多",
                        tint = InkSoft,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                // 预览
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(1f)
                        .clip(RoundedCornerShape(30.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(Color.White, Color(0xFFF3EDE0))
                            )
                        )
                        .border(1.dp, Line, RoundedCornerShape(30.dp))
                ) {
                    ModelViewer(
                        modelFile = File(asset.modelPath),
                        modifier = Modifier.fillMaxSize(),
                        autoRotate = state.settings.autoRotate,
                        onLoaded = { viewerLoaded = true },
                        onError = { viewerFailed = true }
                    )

                    if (!viewerLoaded && !viewerFailed) {
                        Row(
                            Modifier
                                .align(Alignment.Center)
                                .clip(CircleShape)
                                .background(White.copy(alpha = 0.92f))
                                .padding(horizontal = 14.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                color = Mint500,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "正在加载 3D 模型",
                                style = MaterialTheme.typography.labelMedium,
                                color = InkSoft
                            )
                        }
                    }

                    if (asset.demo) {
                        Box(
                            Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                                .clip(CircleShape)
                                .background(White.copy(alpha = 0.9f))
                        ) {
                            Pill(
                                text = "演示模型",
                                background = Color(0xFFF3E2C7),
                                foreground = Color(0xFF7A5A28),
                                leading = Icons.Rounded.AutoAwesome
                            )
                        }
                    }

                    if (viewerFailed) {
                        Text(
                            "3D 预览加载失败，仍可分享或保存模型文件",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkSoft,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(14.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(White.copy(alpha = 0.92f))
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Column(Modifier.padding(horizontal = 22.dp)) {
                    Text(
                        asset.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Ink,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Pill(text = asset.version, background = Mint100, foreground = Mint700)
                        Pill(text = "GLB", background = Color(0xFFF1ECE0), foreground = InkSoft)
                        Pill(
                            text = "${asset.faceCount / 1000}k 面",
                            background = Color(0xFFF1ECE0),
                            foreground = InkSoft
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(Mint700)
                            .clickable { shareModel(context, File(asset.modelPath)) },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Rounded.Share,
                                contentDescription = null,
                                tint = White,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text("分享模型", style = MaterialTheme.typography.labelLarge, color = White)
                        }
                    }
                    Box(
                        Modifier
                            .size(54.dp)
                            .clip(RoundedCornerShape(18.dp))
                            .background(White)
                            .border(1.dp, Line, RoundedCornerShape(18.dp))
                            .clickable {
                                val saved = saveModelToDownloads(
                                    context,
                                    File(asset.modelPath),
                                    asset.name
                                )
                                vm.showToast(
                                    if (saved != null) "已保存到 $saved"
                                    else "系统版本较低，请用「分享模型」导出"
                                )
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Rounded.Download,
                            contentDescription = "保存",
                            tint = Ink,
                            modifier = Modifier.size(21.dp)
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(White)
                            .border(1.dp, Line, RoundedCornerShape(22.dp))
                            .padding(horizontal = 18.dp, vertical = 10.dp)
                    ) {
                        Column {
                            InfoRow("生成引擎", asset.version)
                            ThinDivider()
                            InfoRow("素材张数", "${asset.sourceCount} 张")
                            ThinDivider()
                            InfoRow("模型精度", "${asset.faceCount} 面")
                            ThinDivider()
                            InfoRow("文件大小", humanSize(asset.sizeBytes))
                            ThinDivider()
                            InfoRow("生成耗时", humanDuration(asset.durationMs))
                            if (asset.taskId > 0) {
                                ThinDivider()
                                InfoRow("任务编号", "#${asset.taskId}", Mint700)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                Column(Modifier.padding(horizontal = 20.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(Mint100.copy(alpha = 0.55f))
                            .padding(18.dp)
                    ) {
                        Column {
                            TagText("TECH STACK", color = Mint700)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "本次使用 群核 Aholo Lux3D 图生 3D",
                                style = MaterialTheme.typography.titleSmall,
                                color = Ink
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "素材经 Aholo 资产上传通道入库，再由 Lux3D 异步重建几何与 PBR 材质，产物为可直接进入下游管线的 GLB。",
                                style = MaterialTheme.typography.bodySmall,
                                color = InkSoft,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(50.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, Line, RoundedCornerShape(16.dp))
                        .clickable {
                            vm.go(com.shiwu.scan3d.ui.Screen.Home)
                            vm.startSession()
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text("再扫一个", style = MaterialTheme.typography.labelLarge, color = InkSoft)
                }

                Spacer(Modifier.height(28.dp))
            }
        }
    }

    if (showDelete && asset != null) {
        AlertDialog(
            onDismissRequest = { showDelete = false },
            title = { Text("删除这个作品？") },
            text = { Text("本机上的模型文件会一起删掉，操作不可撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    showDelete = false
                    vm.deleteAsset(asset.id)
                    vm.go(com.shiwu.scan3d.ui.Screen.Library)
                }) { Text("删除", color = com.shiwu.scan3d.ui.theme.Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showDelete = false }) { Text("取消", color = InkSoft) }
            },
            containerColor = White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}
