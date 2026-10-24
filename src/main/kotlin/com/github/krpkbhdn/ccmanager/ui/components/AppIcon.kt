package com.github.krpkbhdn.ccmanager.ui.components

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

private const val GRID = 24f
private const val STROKE = 2f
private val stroke = Stroke(width = STROKE, cap = StrokeCap.Round, join = StrokeJoin.Round)

private fun DrawScope.line(color: Color, x1: Float, y1: Float, x2: Float, y2: Float) =
    drawLine(color, Offset(x1, y1), Offset(x2, y2), strokeWidth = STROKE, cap = StrokeCap.Round)

private fun DrawScope.poly(color: Color, close: Boolean, vararg points: Float) {
    val path = Path().apply {
        moveTo(points[0], points[1])
        for (i in 2 until points.size step 2) lineTo(points[i], points[i + 1])
        if (close) close()
    }
    drawPath(path, color, style = stroke)
}

private fun DrawScope.dot(color: Color, x: Float, y: Float) = drawCircle(color, radius = 1.3f, center = Offset(x, y))

/** Hand-drawn 24-grid line icons, so the UI does not depend on font glyph coverage or icon resources. */
enum class AppIcon(val draw: DrawScope.(Color) -> Unit) {
    Plus({ line(it, 12f, 5f, 12f, 19f); line(it, 5f, 12f, 19f, 12f) }),
    Close({ line(it, 6f, 6f, 18f, 18f); line(it, 18f, 6f, 6f, 18f) }),
    Check({ poly(it, false, 5f, 13f, 10f, 18f, 19f, 7f) }),
    ChevronDown({ poly(it, false, 6f, 9f, 12f, 15f, 18f, 9f) }),
    Folder({ poly(it, true, 3f, 6f, 9f, 6f, 11f, 9f, 21f, 9f, 21f, 19f, 3f, 19f) }),
    Bell({
        val path = Path().apply {
            moveTo(6f, 17f); lineTo(6f, 11f); cubicTo(6f, 4f, 18f, 4f, 18f, 11f); lineTo(18f, 17f)
        }
        drawPath(path, it, style = stroke)
        line(it, 4f, 17f, 20f, 17f)
        line(it, 10f, 20.5f, 14f, 20.5f)
    }),
    Refresh({
        drawArc(it, 40f, 290f, false, Offset(5f, 5f), Size(14f, 14f), style = stroke)
        poly(it, false, 19f, 3.5f, 19f, 8.5f, 14f, 8.5f)
    }),
    Edit({ poly(it, true, 4f, 20f, 5f, 15f, 16f, 4f, 20f, 8f, 9f, 19f) }),
    Trash({
        line(it, 4f, 7f, 20f, 7f)
        poly(it, false, 6f, 7f, 7f, 20f, 17f, 20f, 18f, 7f)
        poly(it, false, 9f, 7f, 9f, 4f, 15f, 4f, 15f, 7f)
    }),
    Home({
        poly(it, false, 3f, 11f, 12f, 4f, 21f, 11f)
        poly(it, false, 6f, 9f, 6f, 20f, 18f, 20f, 18f, 9f)
    }),
    Diff({
        line(it, 12f, 4f, 12f, 10f); line(it, 9f, 7f, 15f, 7f)
        line(it, 9f, 17f, 15f, 17f)
        line(it, 5f, 13.5f, 19f, 13.5f)
    }),
    Activity({ poly(it, false, 3f, 12f, 8f, 12f, 10.5f, 5f, 14f, 19f, 16f, 12f, 21f, 12f) }),
    Terminal({
        poly(it, false, 5f, 8f, 10f, 12f, 5f, 16f)
        line(it, 12f, 17f, 19f, 17f)
    }),
    Alert({
        poly(it, true, 12f, 4f, 21f, 20f, 3f, 20f)
        line(it, 12f, 10f, 12f, 14f)
        dot(it, 12f, 17f)
    }),
    Info({
        drawCircle(it, 9f, Offset(12f, 12f), style = stroke)
        line(it, 12f, 11f, 12f, 16f)
        dot(it, 12f, 8f)
    }),
    Copy({
        drawRoundRect(it, Offset(9f, 9f), Size(11f, 11f), CornerRadius(2f), style = stroke)
        poly(it, false, 15f, 9f, 15f, 5f, 4f, 5f, 4f, 15f, 9f, 15f)
    }),
    Code({
        poly(it, false, 9f, 7f, 4f, 12f, 9f, 17f)
        poly(it, false, 15f, 7f, 20f, 12f, 15f, 17f)
    }),
    Play({ poly(it, true, 8f, 5f, 19f, 12f, 8f, 19f) }),
}

@Composable
fun Glyph(icon: AppIcon, tint: Color, modifier: Modifier = Modifier, size: Dp = 16.dp) {
    Canvas(modifier.size(size)) {
        val factor = this.size.width / GRID
        withTransform({ scale(factor, factor, pivot = Offset.Zero) }) { icon.draw(this, tint) }
    }
}
