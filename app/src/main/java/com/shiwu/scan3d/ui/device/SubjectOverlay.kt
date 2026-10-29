package com.shiwu.scan3d.ui.device

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.shiwu.scan3d.ui.components.SubjectBox
import com.shiwu.scan3d.ui.theme.Amber
import com.shiwu.scan3d.ui.theme.Cream

private data class Viewport(val ox: Float, val oy: Float, val w: Float, val h: Float)

/**
 * 取景屏上的主体框。画面对齐方式和 PreviewView 的 FILL_CENTER 一致，
 * 点按取景屏可以重新指定“要拍的那个物体”。
 */
@Composable
fun SubjectOverlay(
    box: SubjectBox?,
    aspect: Float,
    seedX: Float = 0.5f,
    seedY: Float = 0.5f,
    modifier: Modifier = Modifier,
    onSeed: (Float, Float) -> Unit
) {
    val animated = rememberAnimatedBox(box)

    Canvas(
        modifier.pointerInput(aspect) {
            detectTapGestures { tap ->
                val vp = viewport(size.width.toFloat(), size.height.toFloat(), aspect)
                val nx = (tap.x - vp.ox) / vp.w
                val ny = (tap.y - vp.oy) / vp.h
                if (nx in 0f..1f && ny in 0f..1f) onSeed(nx, ny)
            }
        }
    ) {
        val vp = viewport(size.width, size.height, aspect)
        if (box == null) {
            drawReticle(vp, seedX, seedY)
        } else {
            drawSubjectBox(vp, animated.copy(locked = box.locked))
        }
    }
}

private fun viewport(w: Float, h: Float, aspect: Float): Viewport {
    // PreviewView 是 FILL_CENTER：等比放大到铺满，然后居中裁切。
    val scale = maxOf(w / aspect, h)
    val drawW = aspect * scale
    val drawH = scale
    return Viewport((w - drawW) / 2f, (h - drawH) / 2f, drawW, drawH)
}

/** 还没有锁定主体时，在种子点上画一个准星，告诉用户“这里就是取景点”。 */
private fun DrawScope.drawReticle(vp: Viewport, nx: Float, ny: Float) {
    val x = vp.ox + nx * vp.w
    val y = vp.oy + ny * vp.h
    val arm = 11.dp.toPx()
    val gap = 4.dp.toPx()
    val stroke = 1.2.dp.toPx()
    val color = Amber.copy(alpha = 0.55f)
    listOf(
        Offset(x - arm, y) to Offset(x - gap, y),
        Offset(x + gap, y) to Offset(x + arm, y),
        Offset(x, y - arm) to Offset(x, y - gap),
        Offset(x, y + gap) to Offset(x, y + arm)
    ).forEach { (a, b) -> drawLine(color, a, b, stroke, StrokeCap.Round) }
    drawCircle(Amber.copy(alpha = 0.7f), 1.6.dp.toPx(), Offset(x, y))
}

private fun DrawScope.drawSubjectBox(vp: Viewport, box: SubjectBox) {
    val l = vp.ox + box.left * vp.w
    val t = vp.oy + box.top * vp.h
    val r = vp.ox + box.right * vp.w
    val b = vp.oy + box.bottom * vp.h
    if (r - l < 2f || b - t < 2f) return

    val stroke = 1.8.dp.toPx()
    val color = Amber.copy(alpha = if (box.locked) 0.95f else 0.65f)
    drawRect(
        color = Amber.copy(alpha = 0.16f),
        topLeft = Offset(l, t),
        size = Size(r - l, b - t),
        style = Stroke(1.dp.toPx())
    )

    val arm = minOf(22.dp.toPx(), (r - l) * 0.22f, (b - t) * 0.22f)
    fun bracket(x: Float, y: Float, dx: Float, dy: Float) {
        drawLine(color, Offset(x, y), Offset(x + arm * dx, y), stroke, StrokeCap.Round)
        drawLine(color, Offset(x, y), Offset(x, y + arm * dy), stroke, StrokeCap.Round)
    }
    bracket(l, t, 1f, 1f)
    bracket(r, t, -1f, 1f)
    bracket(l, b, 1f, -1f)
    bracket(r, b, -1f, -1f)

    // 连续几帧稳定后点一个小方块，表示已经咬住主体。
    if (box.locked) {
        drawRect(
            Cream.copy(alpha = 0.85f),
            topLeft = Offset(r - 4.dp.toPx(), b + 4.dp.toPx()),
            size = Size(3.dp.toPx(), 3.dp.toPx())
        )
    }
}

/** 框每次更新都做 ~90ms 的短补间，分割校正时不会“啪”地跳一下。 */
@Composable
private fun rememberAnimatedBox(box: SubjectBox?): SubjectBox {
    val left by animateFloatAsState(box?.left ?: 0f, tween(90), label = "boxLeft")
    val top by animateFloatAsState(box?.top ?: 0f, tween(90), label = "boxTop")
    val right by animateFloatAsState(box?.right ?: 0f, tween(90), label = "boxRight")
    val bottom by animateFloatAsState(box?.bottom ?: 0f, tween(90), label = "boxBottom")
    return SubjectBox(
        left = left,
        top = top,
        right = right,
        bottom = bottom,
        coverage = box?.coverage ?: 0f,
        aspect = box?.aspect ?: (4f / 3f),
        locked = box?.locked ?: false
    )
}
