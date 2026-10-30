package com.shiwu.scan3d.ui.device

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.AmberDim
import com.shiwu.scan3d.ui.theme.Brass
import com.shiwu.scan3d.ui.theme.Cream
import com.shiwu.scan3d.ui.theme.Deck
import com.shiwu.scan3d.ui.theme.glossy

private data class GuideStep(
    val index: String,
    val title: String,
    val body: String,
    val keyLink: Boolean = false
)

val GUIDE_STEP_COUNT: Int get() = GUIDE_STEPS.size

private val GUIDE_STEPS = listOf(
    GuideStep(
        index = "01 / 05",
        title = "对准要拍的东西",
        body = "取景屏中心就是取景点。画面里只有一个物体时，它会自动被框住；点屏幕上别的东西，可以改选目标。"
    ),
    GuideStep(
        index = "02 / 05",
        title = "围着它拍一圈",
        body = "短按旋钮拍一帧，指针每次转 45°。8–12 帧、光线均匀、表面有纹理，重建出来的模型更完整。"
    ),
    GuideStep(
        index = "03 / 05",
        title = "长按提交",
        body = "长按旋钮开始成像，画面会闪一下并折进「资产」。提交后取景器立即复位，可以接着拍下一件，不用等。"
    ),
    GuideStep(
        index = "04 / 05",
        title = "在资产里等结果",
        body = "进度、失败重试、成品预览都在资产页。底片会保留到模型下载完成，成像失败也不用重拍。"
    ),
    GuideStep(
        index = "05 / 05",
        title = "没有密钥也能试",
        body = "配置页底部的「服务舱」可以跳转申请 API Key；没有 Key 时打开演示模式，全流程照常走完。",
        keyLink = true
    )
)

/**
 * 首次启动的操作手册：覆盖整机，一次讲清取景、拍摄、提交和资产。
 */
@Composable
fun GuideOverlay(
    step: Int,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onOpenKeySite: () -> Unit
) {
    val current = GUIDE_STEPS[step.coerceIn(GUIDE_STEPS.indices)]
    val last = step == GUIDE_STEPS.lastIndex

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.74f))
            .pointerInput(Unit) { detectTapGestures { } },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(22.dp)
                .widthIn(max = 360.dp)
                .clip(RoundedCornerShape(5.dp))
                .background(Deck)
                .border(2.dp, Brass.copy(alpha = 0.7f), RoundedCornerShape(5.dp))
                .padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "操作手册",
                    style = MaterialTheme.typography.titleSmall.glossy(),
                    fontSize = 13.sp
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "FIELD MANUAL",
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp, letterSpacing = 2.sp),
                    color = AmberDim
                )
            }

            Spacer(Modifier.height(14.dp))
            Text(
                current.index,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, letterSpacing = 2.sp),
                color = Amber
            )
            Spacer(Modifier.height(4.dp))
            Text(
                current.title,
                style = MaterialTheme.typography.titleMedium,
                color = Cream,
                fontSize = 17.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                current.body,
                style = MaterialTheme.typography.bodySmall,
                color = Cream.copy(alpha = 0.78f),
                fontSize = 11.sp,
                lineHeight = 17.sp
            )

            if (current.keyLink) {
                Spacer(Modifier.height(10.dp))
                PanelAction("打开密钥申请页 ↗", onClick = onOpenKeySite)
            }

            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                GUIDE_STEPS.indices.forEach { i ->
                    Box(
                        Modifier
                            .size(if (i == step) 8.dp else 5.dp)
                            .clip(CircleShape)
                            .background(if (i <= step) Amber else Cream.copy(alpha = 0.2f))
                    )
                }
            }

            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PanelAction("跳过 / SKIP", onClick = onSkip, modifier = Modifier.weight(1f))
                PanelAction(
                    if (last) "开始拍摄 / START" else "下一步 / NEXT",
                    onClick = onNext,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}
