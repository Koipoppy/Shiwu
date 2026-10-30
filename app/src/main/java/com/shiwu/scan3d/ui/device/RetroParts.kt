package com.shiwu.scan3d.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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

/** 细分隔线：一根发丝线，两端各一个小刻线，像仪表上的分栏。 */
@Composable
fun HairlineRule(modifier: Modifier = Modifier, alpha: Float = 0.14f) {
    Canvas(modifier.fillMaxWidth().height(3.5.dp).padding(horizontal = 8.dp)) {
        val y = size.height / 2f
        drawLine(Amber.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 1f)
        listOf(0f, size.width).forEach { x ->
            drawLine(
                Amber.copy(alpha = 0.42f),
                Offset(x, y - 1.6.dp.toPx()),
                Offset(x, y + 1.6.dp.toPx()),
                1f
            )
        }
    }
}

/** 面板上的小指示灯：亮起时带一圈余辉。参数名不叫 size，避免遮住 DrawScope.size。 */
@Composable
fun LedDot(on: Boolean, color: Color = Amber, diameter: Dp = 6.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension / 2f
        if (on) {
            drawCircle(color.copy(alpha = 0.22f), r * 2.3f, c)
            drawCircle(color.copy(alpha = 0.45f), r * 1.5f, c)
            drawCircle(color, r, c)
        } else {
            drawCircle(Color.Black.copy(alpha = 0.62f), r, c)
            drawCircle(color.copy(alpha = 0.26f), r, c, style = Stroke(0.8.dp.toPx()))
        }
    }
}

/** 缩略图上的扫描线 + 一层薄琥珀镀膜，让照片看起来像终端里的读数。 */
@Composable
fun ScanlineOverlay(modifier: Modifier = Modifier, step: Dp = 4.dp, alpha: Float = 0.16f) {
    Canvas(modifier) {
        var y = 0f
        val s = step.toPx()
        while (y < size.height) {
            drawLine(Color.Black.copy(alpha = alpha), Offset(0f, y), Offset(size.width, y), 1f)
            y += s
        }
        drawRect(Amber.copy(alpha = 0.05f))
    }
}

