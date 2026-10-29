package com.shiwu.scan3d.ui.device

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.AmberDim
import com.shiwu.scan3d.ui.theme.Brass
import com.shiwu.scan3d.ui.theme.BrassDark
import com.shiwu.scan3d.ui.theme.BrassLight
import com.shiwu.scan3d.ui.theme.Cream
import com.shiwu.scan3d.ui.theme.Ink
import kotlin.math.cos
import kotlin.math.sin

/**
 * 物理旋钮：短按转一格并触发拍摄，长按压下去提交。
 * [turns] 是累计转过的格数，用来驱动指针角度。
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ShutterKnob(
    turns: Int,
    frames: Int,
    maxFrames: Int,
    enabled: Boolean,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 116.dp
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()

    val angle by animateFloatAsState(
        targetValue = turns * 45f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMediumLow),
        label = "knobAngle"
    )
    val press by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessHigh),
        label = "knobPress"
    )
    val ready = if (enabled) 1f else 0.45f

    Box(
        modifier
            .size(diameter)
            .graphicsLayer {
                translationY = press * 3.dp.toPx()
                scaleX = 1f - press * 0.02f
                scaleY = 1f - press * 0.02f
            }
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                onClick = onTap,
                onLongClick = onLongPress
            )
    ) {
        Canvas(Modifier.size(diameter)) {
            val r = size.minDimension / 2f
            val c = Offset(size.width / 2f, size.height / 2f)

            // 外圈阴影
            drawCircle(Color.Black.copy(alpha = 0.55f), r, c + Offset(0f, 3.dp.toPx()))
            // 滚花外环
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(BrassLight.copy(alpha = ready), Brass.copy(alpha = ready), BrassDark.copy(alpha = ready)),
                    startY = 0f,
                    endY = size.height
                ),
                radius = r,
                center = c
            )
            val teeth = 36
            for (i in 0 until teeth) {
                val a = Math.toRadians(i * (360.0 / teeth))
                val rOut = r * 0.995f
                val rIn = r * 0.90f
                drawLine(
                    color = BrassDark.copy(alpha = 0.55f * ready),
                    start = Offset(c.x + (rIn * cos(a)).toFloat(), c.y + (rIn * sin(a)).toFloat()),
                    end = Offset(c.x + (rOut * cos(a)).toFloat(), c.y + (rOut * sin(a)).toFloat()),
                    strokeWidth = 1.4.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 拍照进度环（帧数）
            val ringR = r * 0.80f
            val stroke = 4.dp.toPx()
            drawArc(
                color = Color.Black.copy(alpha = 0.35f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(c.x - ringR, c.y - ringR),
                size = Size(ringR * 2, ringR * 2),
                style = Stroke(stroke, cap = StrokeCap.Round)
            )
            if (maxFrames > 0) {
                val sweep = 360f * (frames.toFloat() / maxFrames).coerceIn(0f, 1f)
                if (sweep > 0f) {
                    drawArc(
                        color = Amber,
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2, ringR * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }

            // 中央旋帽
            val capR = r * 0.66f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(Color(0xFF35302A), Color(0xFF141210)),
                    center = c + Offset(-capR * 0.25f, -capR * 0.3f),
                    radius = capR * 1.4f
                ),
                radius = capR,
                center = c
            )
            drawCircle(
                color = Brass.copy(alpha = 0.65f * ready),
                radius = capR,
                center = c,
                style = Stroke(1.2.dp.toPx())
            )

            // 指针（随角度旋转）
            val rad = Math.toRadians(angle.toDouble() - 90.0)
            val tipR = capR * 0.72f
            val startR = capR * 0.24f
            drawLine(
                color = Amber,
                start = Offset(c.x + (startR * cos(rad)).toFloat(), c.y + (startR * sin(rad)).toFloat()),
                end = Offset(c.x + (tipR * cos(rad)).toFloat(), c.y + (tipR * sin(rad)).toFloat()),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(Amber.copy(alpha = 0.9f), 2.5.dp.toPx(), c + Offset(
                (startR * cos(rad)).toFloat(),
                (startR * sin(rad)).toFloat()
            ))

            // 中心轴钉
            drawCircle(Ink, r * 0.10f, c)
            drawCircle(BrassDark, r * 0.10f, c, style = Stroke(1.dp.toPx()))
            drawLine(
                color = Cream.copy(alpha = 0.5f),
                start = c + Offset(-r * 0.055f, 0f),
                end = c + Offset(r * 0.055f, 0f),
                strokeWidth = 1.dp.toPx()
            )
        }
    }
}

@Composable
fun KnobLabel(text: String, modifier: Modifier = Modifier) {
    androidx.compose.material3.Text(
        text = text,
        style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
        color = AmberDim,
        modifier = modifier
    )
}
