package com.shiwu.scan3d.ui.device

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.Cream
import com.shiwu.scan3d.ui.theme.Glass
import com.shiwu.scan3d.ui.theme.GlassEdge
import com.shiwu.scan3d.ui.theme.SignalRed

/**
 * CRT 取景屏：黄铜边条 + 墨绿玻璃 + 扫描线 + 暗角 + 缓慢下扫的亮带。
 */
@Composable
fun CrtScreen(
    modifier: Modifier = Modifier,
    hudLeft: String,
    hudRight: String,
    hudBottom: String,
    recording: Boolean = false,
    compact: Boolean = false,
    flash: Float = 0f,
    hudRotation: Int = 0,
    content: @Composable BoxScope.() -> Unit
) {
    val transition = rememberInfiniteTransition(label = "crt")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(5600, easing = LinearEasing), RepeatMode.Restart),
        label = "sweep"
    )
    val recBlink by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "rec"
    )

    Box(
        modifier
            .clip(RoundedCornerShape(if (compact) 20.dp else 26.dp))
            .background(GlassEdge)
            .padding(if (compact) 7.dp else 9.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(17.dp))
                .background(Glass)
        ) {
            // 屏幕内容（相机 / 面板 / HUD）整体随姿态旋转，旋转后仍精确铺满玻璃
            val rot by animateFloatAsState(
                targetValue = hudRotation.toFloat(),
                animationSpec = tween(260),
                label = "contentRotation"
            )
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val quarter = (Math.round(hudRotation / 90f) % 2) != 0
                val boxW = if (quarter) maxHeight else maxWidth
                val boxH = if (quarter) maxWidth else maxHeight
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(boxW, boxH)
                        .graphicsLayer { rotationZ = rot }
                ) {
                    content()
                    HudOverlay(
                        hudLeft = hudLeft,
                        hudRight = hudRight,
                        hudBottom = hudBottom,
                        recording = recording,
                        compact = compact,
                        recBlink = recBlink
                    )
                }
            }

            // 镀膜 + 扫描线 + 下扫亮带 + 暗角
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Amber.copy(alpha = 0.055f))
                var y = 0f
                val step = 3.2.dp.toPx()
                while (y < size.height) {
                    drawLine(
                        color = Color.Black.copy(alpha = 0.16f),
                        start = Offset(0f, y),
                        end = Offset(size.width, y),
                        strokeWidth = 1f
                    )
                    y += step
                }
                val bandH = size.height * 0.22f
                val bandY = sweep * (size.height + bandH) - bandH
                drawRect(
                    brush = Brush.verticalGradient(
                        listOf(Color.Transparent, Amber.copy(alpha = 0.05f), Color.Transparent),
                        startY = bandY,
                        endY = bandY + bandH
                    ),
                    topLeft = Offset(0f, bandY),
                    size = Size(size.width, bandH)
                )
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f)),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.72f
                    )
                )
                drawLine(
                    color = Cream.copy(alpha = 0.035f),
                    start = Offset(0f, size.height * 0.18f),
                    end = Offset(size.width, size.height * 0.02f),
                    strokeWidth = size.height * 0.16f
                )
            }

            // 角标
            Canvas(Modifier.fillMaxSize()) {
                val m = if (compact) 12.dp.toPx() else 16.dp.toPx()
                val len = if (compact) 14.dp.toPx() else 18.dp.toPx()
                val w = 1.6.dp.toPx()
                val c = Amber.copy(alpha = 0.5f)
                fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
                    drawLine(c, Offset(x, y), Offset(x + len * dx, y), w, StrokeCap.Round)
                    drawLine(c, Offset(x, y), Offset(x, y + len * dy), w, StrokeCap.Round)
                }
                bracket(m, m, 1f, 1f)
                bracket(size.width - m, m, -1f, 1f)
                bracket(m, size.height - m, 1f, -1f)
                bracket(size.width - m, size.height - m, -1f, -1f)
            }

            // 快门闪白
            if (flash > 0f) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Cream.copy(alpha = flash * 0.55f))
                )
            }
        }
    }
}

@Composable
private fun BoxScope.HudOverlay(
    hudLeft: String,
    hudRight: String,
    hudBottom: String,
    recording: Boolean,
    compact: Boolean,
    recBlink: Float
) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(if (compact) 15.dp else 20.dp)
    ) {
        Row(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (recording) {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(2.dp))
                        .background(SignalRed.copy(alpha = recBlink))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(
                        "REC",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cream,
                        fontSize = 9.sp
                    )
                }
            } else {
                Text(
                    "● STANDBY",
                    style = MaterialTheme.typography.labelSmall,
                    color = Amber.copy(alpha = 0.7f),
                    fontSize = 9.sp
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                hudRight,
                style = MaterialTheme.typography.labelSmall,
                color = Amber,
                fontSize = if (compact) 10.sp else 11.sp
            )
        }

        if (hudLeft.isNotBlank()) {
            Text(
                hudLeft,
                style = MaterialTheme.typography.labelSmall,
                color = Amber.copy(alpha = 0.75f),
                fontSize = 9.sp,
                modifier = Modifier.align(Alignment.TopStart).padding(top = 18.dp)
            )
        }

        Text(
            hudBottom,
            style = MaterialTheme.typography.labelSmall,
            color = Amber.copy(alpha = 0.8f),
            fontSize = 9.sp,
            modifier = Modifier.align(Alignment.BottomStart)
        )
    }
}
