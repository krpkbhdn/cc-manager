package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha

private const val GRID = 24f
private const val STROKE = 1.8f
private const val FILL_ALPHA = Alpha.SOFT * 2
private val outline = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = STROKE, cap = StrokeCap.Round)

private fun polygon(close: Boolean, vararg points: Float): Path = Path().apply {
    moveTo(points[0], points[1])
    for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
    if (close) close()
}

private fun DrawScope.poly(color: Color, close: Boolean, vararg points: Float) = drawPath(polygon(close, *points), color, style = outline)

private fun DrawScope.tintedFolder(color: Color) {
    val body = Path().apply {
        moveTo(3.5f, 7f); cubicTo(3.5f, 6f, 4f, 5.5f, 5f, 5.5f); lineTo(9.2f, 5.5f); lineTo(11.2f, 8f); lineTo(19f, 8f)
        cubicTo(20f, 8f, 20.5f, 8.5f, 20.5f, 9.5f); lineTo(20.5f, 17.5f); cubicTo(20.5f, 18.5f, 20f, 19f, 19f, 19f)
        lineTo(5f, 19f); cubicTo(4f, 19f, 3.5f, 18.5f, 3.5f, 17.5f); close()
    }
    drawPath(body, color.copy(alpha = FILL_ALPHA))
    drawPath(body, color, style = outline)
}

/** Picker-only 24-grid icons; the shared set in `AppIcon` lacks navigation and drive glyphs. */
enum class PickerIcon(val draw: DrawScope.(Color) -> Unit) {
    FolderTinted({ tintedFolder(it) }),
    FolderPlus({
        tintedFolder(it)
        line(it, 12f, 11.5f, 12f, 16.5f); line(it, 9.5f, 14f, 14.5f, 14f)
    }),
    ArrowLeft({ line(it, 19f, 12f, 5f, 12f); poly(it, false, 11f, 6f, 5f, 12f, 11f, 18f) }),
    ArrowRight({ line(it, 5f, 12f, 19f, 12f); poly(it, false, 13f, 6f, 19f, 12f, 13f, 18f) }),
    ArrowUp({ line(it, 12f, 19f, 12f, 5f); poly(it, false, 6f, 11f, 12f, 5f, 18f, 11f) }),
    ChevronRight({ poly(it, false, 9f, 6f, 15f, 12f, 9f, 18f) }),
    Search({
        drawCircle(it, 6.5f, Offset(10.5f, 10.5f), style = outline)
        line(it, 15.5f, 15.5f, 20f, 20f)
    }),
    Drive({
        drawRoundRect(it, Offset(3.5f, 8f), Size(17f, 9f), CornerRadius(2.5f), style = outline)
        line(it, 7f, 12.5f, 11f, 12.5f)
        drawCircle(it, 1f, Offset(17f, 12.5f))
    }),
    Desktop({
        drawRoundRect(it, Offset(3.5f, 5f), Size(17f, 11f), CornerRadius(2f), style = outline)
        line(it, 9f, 20f, 15f, 20f); line(it, 12f, 16f, 12f, 20f)
    }),
    Document({
        poly(it, true, 6f, 3.5f, 14f, 3.5f, 18f, 7.5f, 18f, 20.5f, 6f, 20.5f)
        poly(it, false, 14f, 3.5f, 14f, 7.5f, 18f, 7.5f)
        line(it, 9f, 12f, 15f, 12f); line(it, 9f, 15.5f, 15f, 15.5f)
    }),
    Download({
        line(it, 12f, 4f, 12f, 15f); poly(it, false, 7f, 10.5f, 12f, 15.5f, 17f, 10.5f)
        poly(it, false, 5f, 19f, 19f, 19f)
    }),
    Clock({
        drawCircle(it, 8.5f, Offset(12f, 12f), style = outline)
        poly(it, false, 12f, 7.5f, 12f, 12f, 15.5f, 14f)
    }),
    Eye({
        val lid = Path().apply { moveTo(2.8f, 12f); cubicTo(6f, 6f, 18f, 6f, 21.2f, 12f); cubicTo(18f, 18f, 6f, 18f, 2.8f, 12f) }
        drawPath(lid, it, style = outline)
        drawCircle(it, 2.8f, Offset(12f, 12f), style = outline)
    }),
    Work({
        drawRoundRect(it, Offset(3.5f, 8f), Size(17f, 11.5f), CornerRadius(2.5f), style = outline)
        poly(it, false, 9f, 8f, 9f, 5.5f, 15f, 5.5f, 15f, 8f)
    }),
    Lock({
        drawRoundRect(it, Offset(5.5f, 10.5f), Size(13f, 9f), CornerRadius(2.5f), style = outline)
        poly(it, false, 8.5f, 10.5f, 8.5f, 8f, 12f, 4.5f, 15.5f, 8f, 15.5f, 10.5f)
    }),
}

@Composable
fun PickerGlyph(icon: PickerIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val factor = this.size.width / GRID
        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) { icon.draw(this, tint) }
    }
}
