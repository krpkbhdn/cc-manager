package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind

/** High-contrast on screenshots of any theme. */
val MarkColor = Color(0xFFFF3B30)

private const val STROKE_PX = 2f
private const val SELECTED_STROKE_PX = 4f
private const val PIN_RADIUS_PX = 7f
private const val BADGE_RADIUS_PX = 10f
private const val BADGE_TEXT_SP = 11
private const val FILL_ALPHA = 0.12f

/** Screen rectangle of a mark; a pin is a zero-size rectangle at its point. */
fun markScreenRect(mark: MarkDraft, viewport: Viewport, imageW: Float, imageH: Float): Rect {
    val (left, top) = viewport.toScreen((mark.x * imageW).toFloat(), (mark.y * imageH).toFloat())
    return Rect(Offset(left, top), Size((mark.w * imageW * viewport.scale).toFloat(), (mark.h * imageH * viewport.scale).toFloat()))
}

/** Draws one mark with its number badge; [index] is the position in this image's list (label is index + 1, numbering restarts per image). */
fun DrawScope.drawMark(mark: MarkDraft, index: Int, selected: Boolean, viewport: Viewport, imageW: Float, imageH: Float, measurer: TextMeasurer) {
    val rect = markScreenRect(mark, viewport, imageW, imageH)
    val stroke = Stroke(if (selected) SELECTED_STROKE_PX else STROKE_PX)
    when (mark.kind) {
        MarkKind.RECTANGLE -> drawRect(MarkColor, rect.topLeft, rect.size, style = stroke)
        MarkKind.ELLIPSE -> drawOval(MarkColor, rect.topLeft, rect.size, style = stroke)
        MarkKind.PIN -> drawCircle(MarkColor, PIN_RADIUS_PX, rect.topLeft)
        MarkKind.FREEFORM -> drawPolygon(mark, viewport, imageW, imageH, stroke, FILL_ALPHA)
    }
    drawBadge(MarkGeometry.labelOf(index), badgeCentre(mark, rect, viewport, imageW, imageH), measurer)
}

/** Un-numbered outline of a mark still being dragged. */
fun DrawScope.drawPreview(mark: MarkDraft, viewport: Viewport, imageW: Float, imageH: Float) {
    val rect = markScreenRect(mark, viewport, imageW, imageH)
    val stroke = Stroke(STROKE_PX)
    when (mark.kind) {
        MarkKind.ELLIPSE -> drawOval(MarkColor, rect.topLeft, rect.size, style = stroke)
        MarkKind.FREEFORM -> drawPolygon(mark, viewport, imageW, imageH, stroke, FILL_ALPHA)
        else -> drawRect(MarkColor, rect.topLeft, rect.size, style = stroke)
    }
}

/** Screen position of a normalised image point. */
internal fun screenPoint(x: Double, y: Double, viewport: Viewport, imageW: Float, imageH: Float): Offset =
    viewport.toScreen((x * imageW).toFloat(), (y * imageH).toFloat()).let { Offset(it.first, it.second) }

private fun DrawScope.drawPolygon(mark: MarkDraft, viewport: Viewport, imageW: Float, imageH: Float, stroke: Stroke, fillAlpha: Float) {
    if (mark.points.size < 2) return
    val path = Path()
    mark.points.forEachIndexed { i, p ->
        val at = screenPoint(p.x, p.y, viewport, imageW, imageH)
        if (i == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
    }
    path.close()
    drawPath(path, MarkColor.copy(alpha = fillAlpha))
    drawPath(path, MarkColor, style = stroke)
}

/** Badge centre up-left of the corner so it clears the corner handle; clamped into [bounds], where it may overlap (handles are drawn on top and win hit-testing). */
internal fun badgeCenter(corner: Offset, radius: Float, bounds: Rect): Offset {
    val x = (corner.x - radius).coerceAtMost(bounds.right - radius).coerceAtLeast(bounds.left + radius)
    val y = (corner.y - radius).coerceAtMost(bounds.bottom - radius).coerceAtLeast(bounds.top + radius)
    return Offset(x, y)
}

private fun badgeCentre(mark: MarkDraft, rect: Rect, viewport: Viewport, imageW: Float, imageH: Float): Offset {
    val (left, top) = viewport.toScreen(0f, 0f)
    val bounds = Rect(Offset(left, top), Size(imageW * viewport.scale, imageH * viewport.scale))
    val corner = if (mark.kind == MarkKind.PIN) Offset(rect.left + BADGE_RADIUS_PX, rect.top - PIN_RADIUS_PX) else rect.topLeft
    return badgeCenter(corner, BADGE_RADIUS_PX, bounds)
}

private fun DrawScope.drawBadge(label: String, centre: Offset, measurer: TextMeasurer) {
    drawCircle(MarkColor, BADGE_RADIUS_PX, centre)
    val text = measurer.measure(label, TextStyle(color = Color.White, fontSize = BADGE_TEXT_SP.sp, fontWeight = FontWeight.Bold))
    drawText(text, topLeft = Offset(centre.x - text.size.width / 2f, centre.y - text.size.height / 2f))
}
