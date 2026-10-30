package com.shiwu.scan3d.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.AmberDim
import com.shiwu.scan3d.ui.theme.Cream

/** 切角矩形：小面板用，和取景屏的大圆角形成对比。 */
class ChamferShape(private val cut: Dp) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val c = minOf(with(density) { cut.toPx() }, size.minDimension / 2f)
        val w = size.width
        val h = size.height
        return Outline.Generic(
            Path().apply {
                moveTo(c, 0f)
                lineTo(w - c, 0f)
                lineTo(w, c)
                lineTo(w, h - c)
                lineTo(w - c, h)
                lineTo(c, h)
                lineTo(0f, h - c)
                lineTo(0f, c)
                close()
            }
        )
    }
}

/** 凹进机身的按键座：上沿落影、下沿受光，按键本体画在它里面。 */
internal fun DrawScope.drawWell(c: Offset, r: Float) {
    val ro = r + 3.5.dp.toPx()
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(Color(0xFF050403), Color(0xFF15110C)),
            startY = c.y - ro,
            endY = c.y + ro
        ),
        radius = ro,
        center = c
    )
    drawCircle(
        brush = Brush.verticalGradient(
            listOf(
                Color.Black.copy(alpha = 0.9f),
                Color.Black.copy(alpha = 0f),
                Cream.copy(alpha = 0.2f)
            ),
            startY = c.y - ro,
            endY = c.y + ro
        ),
        radius = ro - 0.6.dp.toPx(),
        center = c,
        style = Stroke(1.2.dp.toPx())
    )
}

/** 分段进度条：亮起的段带一圈余辉。 */
@Composable
fun SegmentBar(filled: Int, total: Int, height: Dp, modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        if (total <= 0) return@Canvas
        val gap = 2.dp.toPx()
        val w = (size.width - gap * (total - 1)) / total
        val halo = 1.5.dp.toPx()
        for (i in 0 until total) {
            val x = i * (w + gap)
            if (i < filled) {
                drawRect(
                    Amber.copy(alpha = 0.2f),
                    Offset(x - halo, -halo),
                    Size(w + halo * 2, size.height + halo * 2)
                )
                drawRect(Amber, Offset(x, 0f), Size(w, size.height))
            } else {
                drawRect(Cream.copy(alpha = 0.10f), Offset(x, 0f), Size(w, size.height))
            }
        }
    }
}

/** 黄黑警示斜纹，放在服务舱、弹窗这类“要小心”的面板顶边。 */
@Composable
fun HazardStripe(modifier: Modifier = Modifier, height: Dp = 5.dp) {
    Canvas(modifier.fillMaxWidth().height(height)) {
        drawRect(Color(0xFF0B0A07))
        val h = size.height
        val stripe = 5.dp.toPx()
        clipRect {
            var x = -h
            while (x < size.width) {
                val p = Path().apply {
                    moveTo(x, h)
                    lineTo(x + h, 0f)
                    lineTo(x + h + stripe, 0f)
                    lineTo(x + stripe, h)
                    close()
                }
                drawPath(p, AmberDim.copy(alpha = 0.9f))
                x += stripe * 2f
            }
        }
    }
}
