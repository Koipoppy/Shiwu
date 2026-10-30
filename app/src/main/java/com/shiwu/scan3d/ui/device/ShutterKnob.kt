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

            // 底部多层阴影（更深邃）
            drawCircle(Color.Black.copy(alpha = 0.65f), r * 1.02f, c + Offset(0f, 4.dp.toPx()))
            drawCircle(Color.Black.copy(alpha = 0.35f), r * 1.05f, c + Offset(0f, 6.dp.toPx()))
            
            // 滚花外环（加强金属渐变）
            drawCircle(
                brush = Brush.verticalGradient(
                    listOf(
                        BrassLight.copy(alpha = ready * 0.95f),
                        Brass.copy(alpha = ready * 0.85f),
                        BrassDark.copy(alpha = ready * 0.75f),
                        Color(0xFF6A4B25).copy(alpha = ready * 0.8f)
                    ),
                    startY = 0f,
                    endY = size.height
                ),
                radius = r,
                center = c
            )
            
            // 外环高光边缘
            drawCircle(
                color = BrassLight.copy(alpha = 0.45f * ready),
                radius = r,
                center = c + Offset(0f, -1.dp.toPx()),
                style = Stroke(1.2.dp.toPx())
            )
            
            // 更密集的滚花齿纹（银翼杀手风格）
            val teeth = 48
            for (i in 0 until teeth) {
                val a = Math.toRadians(i * (360.0 / teeth))
                val rOut = r * 0.995f
                val rIn = r * 0.88f
                drawLine(
                    color = BrassDark.copy(alpha = 0.65f * ready),
                    start = Offset(c.x + (rIn * cos(a)).toFloat(), c.y + (rIn * sin(a)).toFloat()),
                    end = Offset(c.x + (rOut * cos(a)).toFloat(), c.y + (rOut * sin(a)).toFloat()),
                    strokeWidth = 1.6.dp.toPx(),
                    cap = StrokeCap.Round
                )
            }

            // 拍照进度环（更粗、更醒目）
            val ringR = r * 0.78f
            val stroke = 5.dp.toPx()
            drawArc(
                color = Color.Black.copy(alpha = 0.45f),
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
                        brush = Brush.sweepGradient(
                            listOf(Amber, Amber.copy(alpha = 0.85f), Amber),
                            center = c
                        ),
                        startAngle = -90f,
                        sweepAngle = sweep,
                        useCenter = false,
                        topLeft = Offset(c.x - ringR, c.y - ringR),
                        size = Size(ringR * 2, ringR * 2),
                        style = Stroke(stroke, cap = StrokeCap.Round)
                    )
                }
            }

            // 中央旋帽（更强的金属深度）
            val capR = r * 0.64f
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        Color(0xFF3D3830),
                        Color(0xFF2A251E),
                        Color(0xFF14110D)
                    ),
                    center = c + Offset(-capR * 0.2f, -capR * 0.25f),
                    radius = capR * 1.5f
                ),
                radius = capR,
                center = c
            )
            
            // 旋帽边缘高光
            drawCircle(
                color = Brass.copy(alpha = 0.75f * ready),
                radius = capR,
                center = c,
                style = Stroke(1.5.dp.toPx())
            )
            
            // 内部凹痕
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = capR * 0.92f,
                center = c + Offset(0.5.dp.toPx(), 0.8.dp.toPx()),
                style = Stroke(1.2.dp.toPx())
            )

            // 指针（随角度旋转，加辉光）
            val rad = Math.toRadians(angle.toDouble() - 90.0)
            val tipR = capR * 0.70f
            val startR = capR * 0.22f
            
            // 指针辉光
            drawLine(
                color = Amber.copy(alpha = 0.3f),
                start = Offset(c.x + (startR * cos(rad)).toFloat(), c.y + (startR * sin(rad)).toFloat()),
                end = Offset(c.x + (tipR * cos(rad)).toFloat(), c.y + (tipR * sin(rad)).toFloat()),
                strokeWidth = 7.dp.toPx(),
                cap = StrokeCap.Round
            )
            // 指针主体
            drawLine(
                color = Amber,
                start = Offset(c.x + (startR * cos(rad)).toFloat(), c.y + (startR * sin(rad)).toFloat()),
                end = Offset(c.x + (tipR * cos(rad)).toFloat(), c.y + (tipR * sin(rad)).toFloat()),
                strokeWidth = 4.5.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                Amber.copy(alpha = 0.95f), 
                3.dp.toPx(), 
                c + Offset(
                    (startR * cos(rad)).toFloat(),
                    (startR * sin(rad)).toFloat()
                )
            )

            // 中心轴钉（更强的金属质感）
            drawCircle(Color(0xFF0A0808), r * 0.12f, c)
            drawCircle(BrassDark.copy(alpha = 0.85f), r * 0.12f, c, style = Stroke(1.4.dp.toPx()))
            drawLine(
                color = Cream.copy(alpha = 0.6f),
                start = c + Offset(-r * 0.065f, 0f),
                end = c + Offset(r * 0.065f, 0f),
                strokeWidth = 1.2.dp.toPx()
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
