package com.shiwu.scan3d.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiwu.scan3d.data.AppSettings
import com.shiwu.scan3d.ui.AppState
import com.shiwu.scan3d.ui.AppViewModel
import com.shiwu.scan3d.ui.components.AmbientBackdrop
import com.shiwu.scan3d.ui.components.ScreenHeader
import com.shiwu.scan3d.ui.components.TagText
import com.shiwu.scan3d.ui.theme.Danger
import com.shiwu.scan3d.ui.theme.Ink
import com.shiwu.scan3d.ui.theme.InkFaint
import com.shiwu.scan3d.ui.theme.InkSoft
import com.shiwu.scan3d.ui.theme.Line
import com.shiwu.scan3d.ui.theme.Mint100
import com.shiwu.scan3d.ui.theme.Mint50
import com.shiwu.scan3d.ui.theme.Mint500
import com.shiwu.scan3d.ui.theme.Mint700
import com.shiwu.scan3d.ui.theme.White

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    state: AppState,
    bottomPadding: PaddingValues
) {
    var showKeyDialog by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf(false) }
    val settings = state.settings

    AmbientBackdrop {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(bottom = bottomPadding.calculateBottomPadding())
        ) {
            ScreenHeader(title = "设置", onBack = { vm.back() })

            Spacer(Modifier.height(4.dp))

            // ---- 接入 ----
            GroupLabel("Aholo 接入")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(White)
                    .border(1.dp, Line, RoundedCornerShape(24.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showKeyDialog = true }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("API Key", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            maskKey(settings.apiKey),
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                    }
                    Text(
                        "修改",
                        style = MaterialTheme.typography.labelMedium,
                        color = Mint700,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Mint50)
                            .clickable { showKeyDialog = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }

                LineBox()

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("接口区域", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(settings.baseUrl, style = MaterialTheme.typography.labelSmall, color = InkFaint)
                    }
                    Row(
                        Modifier
                            .clip(CircleShape)
                            .background(Mint50)
                            .padding(3.dp)
                    ) {
                        RegionChip("国内", !settings.globalRegion) {
                            vm.updateSettings(settings.copy(globalRegion = false))
                        }
                        RegionChip("国际", settings.globalRegion) {
                            vm.updateSettings(settings.copy(globalRegion = true))
                        }
                    }
                }

                LineBox()

                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("连接测试", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(3.dp))
                        ProbeStatus(state)
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Mint700)
                            .clickable { vm.testConnection() }
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    ) {
                        if (state.probe.checking) {
                            CircularProgressIndicator(
                                color = White,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text("测试", style = MaterialTheme.typography.labelMedium, color = White)
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 生成 ----
            GroupLabel("生成")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(White)
                    .border(1.dp, Line, RoundedCornerShape(24.dp))
            ) {
                SwitchRow(
                    title = "演示模式",
                    caption = "不调用接口，用内置模型走完整流程",
                    checked = settings.demoMode,
                    onCheckedChange = { vm.updateSettings(settings.copy(demoMode = it)) }
                )
                LineBox()
                SwitchRow(
                    title = "结果自动旋转",
                    caption = "进入详情页时模型缓慢自转",
                    checked = settings.autoRotate,
                    onCheckedChange = { vm.updateSettings(settings.copy(autoRotate = it)) }
                )
            }

            Spacer(Modifier.height(24.dp))

            // ---- 存储 ----
            GroupLabel("存储")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(White)
                    .border(1.dp, Line, RoundedCornerShape(24.dp))
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { showClearDialog = true }
                        .padding(18.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("清空本地资产", style = MaterialTheme.typography.titleSmall, color = Ink)
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "删除 ${state.assets.size} 个模型与全部素材",
                            style = MaterialTheme.typography.labelSmall,
                            color = InkFaint
                        )
                    }
                    Text("清空", style = MaterialTheme.typography.labelMedium, color = Danger)
                }
            }

            Spacer(Modifier.height(24.dp))

            // ---- 关于 ----
            GroupLabel("关于")
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(White)
                    .border(1.dp, Line, RoundedCornerShape(24.dp))
                    .padding(18.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TagText("SHIWU 3D", color = Mint700)
                    Spacer(Modifier.weight(1f))
                    Text("v1.0", style = MaterialTheme.typography.labelSmall, color = InkFaint)
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "拾物是一款 3D 扫描建模工具：手机环绕拍摄，群核 Aholo Lux3D 重建几何与 PBR 材质，产出可直接使用的 GLB。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft,
                    lineHeight = 19.sp
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    MiniTag("Lux3D")
                    MiniTag("图生 3D")
                    MiniTag("PBR")
                    MiniTag("GLB")
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }

    if (showKeyDialog) {
        KeyDialog(
            initial = settings.apiKey,
            onDismiss = { showKeyDialog = false },
            onConfirm = { key ->
                vm.updateSettings(settings.copy(apiKey = key.trim()))
                showKeyDialog = false
            }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空本地资产？") },
            text = { Text("会删除所有已生成的模型和拍摄素材，不可恢复。") },
            confirmButton = {
                TextButton(onClick = {
                    showClearDialog = false
                    vm.clearAllAssets()
                }) { Text("清空", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) { Text("取消", color = InkSoft) }
            },
            containerColor = White,
            shape = RoundedCornerShape(24.dp)
        )
    }
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelMedium,
        color = InkFaint,
        modifier = Modifier.padding(start = 26.dp, bottom = 8.dp, top = 4.dp)
    )
}

@Composable
private fun LineBox() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp)
            .height(1.dp)
            .background(Line.copy(alpha = 0.7f))
    )
}

@Composable
private fun RegionChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(CircleShape)
            .background(if (selected) White else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Mint700 else InkFaint,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun ProbeStatus(state: AppState) {
    val probe = state.probe
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            probe.checking -> {
                CircularProgressIndicator(
                    color = Mint500,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(11.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text("正在连接…", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
            probe.message.isNotBlank() && probe.ok -> {
                Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = Mint700, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(probe.message, style = MaterialTheme.typography.labelSmall, color = Mint700)
            }
            probe.message.isNotBlank() -> {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = Danger, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text(probe.message, style = MaterialTheme.typography.labelSmall, color = Danger)
            }
            else -> {
                Icon(Icons.Rounded.CloudOff, contentDescription = null, tint = InkFaint, modifier = Modifier.size(12.dp))
                Spacer(Modifier.width(6.dp))
                Text("尚未测试", style = MaterialTheme.typography.labelSmall, color = InkFaint)
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    caption: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink)
            Spacer(Modifier.height(3.dp))
            Text(caption, style = MaterialTheme.typography.labelSmall, color = InkFaint)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = White,
                checkedTrackColor = Mint700,
                uncheckedThumbColor = White,
                uncheckedTrackColor = Color(0xFFD9D5CA),
                uncheckedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun MiniTag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Mint100)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = Mint700)
    }
}

@Composable
private fun KeyDialog(initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var value by remember { mutableStateOf(initial) }
    var visible by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Aholo API Key") },
        text = {
            Column {
                Text(
                    "在 lux3d.aholo3d.cn 的「控制台 → API Key」申请，只保存在本机。",
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSoft
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = value,
                    onValueChange = { value = it },
                    singleLine = true,
                    visualTransformation = if (visible) androidx.compose.ui.text.input.VisualTransformation.None
                    else androidx.compose.ui.text.input.PasswordVisualTransformation(),
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = {
                        Box(
                            Modifier
                                .size(38.dp)
                                .clickable { visible = !visible },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                                tint = InkFaint,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mint500,
                        unfocusedBorderColor = Line,
                        cursorColor = Mint700
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Check, contentDescription = null, tint = Mint700, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("保存", color = Mint700)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消", color = InkSoft) }
        },
        containerColor = White,
        shape = RoundedCornerShape(24.dp)
    )
}

private fun maskKey(key: String): String {
    if (key.isBlank()) return "未设置 · 将使用演示模式"
    if (key.length <= 8) return "••••••••"
    return key.take(4) + "••••••" + key.takeLast(4)
}
