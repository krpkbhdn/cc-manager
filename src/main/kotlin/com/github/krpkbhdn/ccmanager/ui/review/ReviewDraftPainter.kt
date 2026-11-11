package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.github.krpkbhdn.ccmanager.review.MarkPoint

private const val RING_RADIUS_PX = 9f
private const val RING_STROKE_PX = 2.5f
private const val LINE_PX = 2f
private const val RUBBER_ALPHA = 0.6f

/** The polygon being placed: a polyline through the points, a dot on each, a rubber band to the [cursor] and a ring on the first point when a click there would close it. */
internal fun DrawScope.drawPolygonDraft(points: List<MarkPoint>, cursor: Offset?, viewport: Viewport, imageW: Float, imageH: Float, accent: Color) {
    if (points.isEmpty()) return
    val screen = points.map { screenPoint(it.x, it.y, viewport, imageW, imageH) }
    screen.zipWithNext { a, b -> drawLine(MarkColor, a, b, LINE_PX) }
    cursor?.let { drawLine(MarkColor.copy(alpha = RUBBER_ALPHA), screen.last(), it, LINE_PX) }
    screen.forEach { drawDot(it, accent, 1f) }
    if (cursor != null && screen.size >= 3 && (cursor - screen.first()).getDistance() <= HIT_TOLERANCE_PX) {
        drawCircle(accent, RING_RADIUS_PX, screen.first(), style = Stroke(RING_STROKE_PX))
    }
}
