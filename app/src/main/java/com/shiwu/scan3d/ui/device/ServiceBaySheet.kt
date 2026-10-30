package com.shiwu.scan3d.ui.device

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.AmberDim
import com.shiwu.scan3d.ui.theme.Brass
import com.shiwu.scan3d.ui.theme.Cream
import com.shiwu.scan3d.ui.theme.SignalRed
import com.shiwu.scan3d.ui.theme.glossy
import com.shiwu.scan3d.ui.theme.glow
import kotlinx.coroutines.launch

/**
 * 服务舱：在应用内走完「申请 → 复制 → 校验 → 写入」，
 * 不用切到浏览器再回来手动粘贴。
 */
@Composable
fun ServiceBaySheet(
    initialKey: String,
    globalRegion: Boolean,
    autoWeb: Boolean,
    onVerify: suspend (String, Boolean) -> Result<Long>,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var key by remember { mutableStateOf(initialKey) }
    var reveal by remember { mutableStateOf(false) }
    var webOpen by remember { mutableStateOf(autoWeb) }
    var checking by remember { mutableStateOf(false) }
    var verdict by remember { mutableStateOf<Pair<Boolean, String>?>(null) }

    fun readClipboard(): String {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return ""
        val clip = cm.primaryClip ?: return ""
        if (clip.itemCount == 0) return ""
        return clip.getItemAt(0).coerceToText(context)?.toString().orEmpty().trim()
    }

    val shape = ChamferShape(8.dp)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.82f))
                .padding(14.dp),
            contentAlignment = Alignment.Center
        ) {
            if (webOpen) {
                // 应用内打开申请页：登录、申请、复制都在这里完成
                Column(
                    Modifier
                        .fillMaxSize()
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF14170F), Color(0xFF070906))))
                        .border(1.dp, Brass.copy(alpha = 0.5f), shape)
                ) {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "LUX3D 控制台",
                            style = MaterialTheme.typography.titleSmall.glossy(),
                            color = Amber
                        )
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .clip(ChamferShape(3.dp))
                                .border(1.dp, Brass.copy(alpha = 0.5f), ChamferShape(3.dp))
                                .clickable {
                                    webOpen = false
                                    // 从控制台回来时，如果剪贴板里有东西就顺手带进输入框
                                    if (key.isBlank()) {
                                        val clip = readClipboard()
                                        if (clip.isNotBlank() && clip.length <= 128 && !clip.contains('\n')) {
                                            key = clip
                                        }
                                    }
                                }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("完成", style = MaterialTheme.typography.labelMedium, color = Amber)
                        }
                    }
                    HairlineRule(Modifier.padding(horizontal = 6.dp))
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            WebView(ctx).apply {
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.useWideViewPort = true
                                settings.loadWithOverviewMode = true
                                webViewClient = WebViewClient()
                                loadUrl(API_KEY_URL)
                            }
                        }
                    )
                }
            } else {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 620.dp)
                        .clip(shape)
                        .background(Brush.verticalGradient(listOf(Color(0xFF15180F), Color(0xFF080A06))))
                        .border(1.dp, Brass.copy(alpha = 0.55f), shape)
                ) {
                    HazardStripe(height = 3.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "服务舱 / SERVICE BAY",
                                style = MaterialTheme.typography.titleSmall.glossy(),
                                color = Amber
                            )
                            Spacer(Modifier.weight(1f))
                            Text(
                                if (globalRegion) "GLOBAL" else "CN",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 1.6.sp),
                                color = AmberDim
                            )
                        }
                        Text(
                            "三步拿到密钥，全程不用离开应用",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
                            color = Cream.copy(alpha = 0.55f)
                        )

                        Spacer(Modifier.height(10.dp))
                        HairlineRule()

                        StepRow(
                            index = "01",
                            title = "打开申请页",
                            desc = "在应用内登录 Lux3D 控制台，生成一个 API Key"
                        ) {
                            Box(Modifier.width(104.dp)) {
                                PanelAction("应用内打开", onClick = { webOpen = true })
                            }
                        }
                        Text(
                            "应用内登录被挡？改用系统浏览器 ↗",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = AmberDim.copy(alpha = 0.75f),
                            modifier = Modifier
                                .padding(start = 17.dp, bottom = 2.dp)
                                .clickable {
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(API_KEY_URL))
                                        )
                                    }
                                }
                        )

                        HairlineRule()

                        StepRow(
                            index = "02",
                            title = "复制密钥",
                            desc = "在控制台复制那串 Key，回到这里直接粘贴"
                        ) {
                            Box(Modifier.width(104.dp)) {
                                PanelAction(
                                    "读剪贴板",
                                    onClick = {
                                        val clip = readClipboard()
                                        if (clip.isBlank()) {
                                            verdict = false to "剪贴板是空的"
                                        } else {
                                            key = clip.take(128)
                                            verdict = null
                                        }
                                    }
                                )
                            }
                        }

                        HairlineRule()

                        StepRow(
                            index = "03",
                            title = "校验并写入",
                            desc = "打一次任务列表确认 Key 有效，通过后只存本机"
                        ) { Spacer(Modifier.width(1.dp)) }

                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = key,
                                onValueChange = {
                                    key = it.take(128)
                                    verdict = null
                                },
                                singleLine = true,
                                visualTransformation = if (reveal) {
                                    androidx.compose.ui.text.input.VisualTransformation.None
                                } else {
                                    androidx.compose.ui.text.input.PasswordVisualTransformation()
                                },
                                placeholder = {
                                    Text(
                                        "在这里粘贴 API Key",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Cream.copy(alpha = 0.3f)
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodySmall,
                                shape = ChamferShape(4.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = Amber,
                                    unfocusedBorderColor = Brass.copy(alpha = 0.4f),
                                    focusedTextColor = Cream,
                                    unfocusedTextColor = Cream,
                                    cursorColor = Amber,
                                    focusedContainerColor = Color(0xFF11140E),
                                    unfocusedContainerColor = Color(0xFF11140E)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (reveal) "隐藏" else "显示",
                                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.5.sp),
                                color = AmberDim,
                                modifier = Modifier.clickable { reveal = !reveal }
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        val v = verdict
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LedDot(
                                on = v?.first == true,
                                color = when {
                                    checking -> AmberDim
                                    v?.first == true -> Amber
                                    v != null -> SignalRed
                                    else -> Cream.copy(alpha = 0.4f)
                                },
                                diameter = 5.dp
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                when {
                                    checking -> "校验中…"
                                    v == null -> if (key.isBlank()) "等待密钥" else "未校验"
                                    else -> v.second
                                },
                                style = MaterialTheme.typography.labelSmall
                                    .copy(fontSize = 8.5.sp, letterSpacing = 0.8.sp)
                                    .glow(
                                        if (v?.first == true) Amber else Cream,
                                        6f,
                                        0.35f
                                    ),
                                color = when {
                                    v?.first == true -> Amber
                                    v != null -> SignalRed
                                    else -> Cream.copy(alpha = 0.5f)
                                }
                            )
                        }

                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            PanelAction(
                                "取消",
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f)
                            )
                            PanelAction(
                                if (checking) "校验中…" else "校验并写入",
                                onClick = {
                                    val candidate = key.trim()
                                    if (candidate.isBlank()) {
                                        verdict = false to "先粘贴密钥"
                                    } else {
                                        scope.launch {
                                            checking = true
                                            verdict = null
                                            val failure = onVerify(candidate, globalRegion).exceptionOrNull()
                                            checking = false
                                            if (failure == null) {
                                                onSave(candidate)
                                                onDismiss()
                                            } else {
                                                verdict = false to (failure.message ?: "校验失败")
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !checking
                            )
                        }

                        Spacer(Modifier.height(6.dp))
                        Text(
                            "没有网络也能先存着 · 点这里跳过校验直接写入",
                            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                            color = AmberDim.copy(alpha = 0.75f),
                            modifier = Modifier.clickable {
                                val candidate = key.trim()
                                if (candidate.isNotBlank()) {
                                    onSave(candidate)
                                    onDismiss()
                                } else {
                                    verdict = false to "先粘贴密钥"
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

/** 一步一行：序号 + 标题 + 说明，右侧放该步的操作按钮。 */
@Composable
private fun StepRow(
    index: String,
    title: String,
    desc: String,
    action: @Composable () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            index,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
            color = AmberDim.copy(alpha = 0.75f),
            modifier = Modifier.width(17.dp)
        )
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Cream)
            Text(
                desc,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                color = Cream.copy(alpha = 0.45f)
            )
        }
        Spacer(Modifier.width(8.dp))
        action()
    }
}
