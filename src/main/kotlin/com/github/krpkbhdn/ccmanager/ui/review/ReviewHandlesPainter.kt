package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind

private const val HANDLE_SIZE_PX = 9f
private const val HANDLE_BORDER_PX = 1.5f
private const val GHOST_ALPHA = 0.6f

/** Resize squares for a selected rectangle or ellipse, round vertex dots for a freeform; fixed screen size. */
internal fun DrawScope.drawHandles(mark: MarkDraft, viewport: Viewport, imageW: Float, imageH: Float, accent: Color) {
    when (mark.kind) {
        MarkKind.PIN -> Unit
        MarkKind.FREEFORM -> mark.points.forEach { drawDot(screenPoint(it.x, it.y, viewport, imageW, imageH), accent, 1f) }
        else -> {
            val r = markScreenRect(mark, viewport, imageW, imageH)
            val xs = listOf(r.left, r.center.x, r.right)
            val ys = listOf(r.top, r.center.y, r.bottom)
            xs.forEach { x -> ys.forEach { y -> if (!(x == r.center.x && y == r.center.y)) drawSquare(Offset(x, y), accent) } }
        }
    }
}

/** The vertex a press on the selected freeform's edge would insert. */
internal fun DrawScope.drawGhostVertex(at: Offset, accent: Color) = drawDot(at, accent, GHOST_ALPHA)

private fun DrawScope.drawSquare(centre: Offset, accent: Color) {
    val topLeft = Offset(centre.x - HANDLE_SIZE_PX / 2, centre.y - HANDLE_SIZE_PX / 2)
    drawRect(Color.White, topLeft, Size(HANDLE_SIZE_PX, HANDLE_SIZE_PX))
    drawRect(accent, topLeft, Size(HANDLE_SIZE_PX, HANDLE_SIZE_PX), style = Stroke(HANDLE_BORDER_PX))
}

internal fun DrawScope.drawDot(centre: Offset, accent: Color, alpha: Float) {
    drawCircle(Color.White.copy(alpha = alpha), HANDLE_SIZE_PX / 2, centre)
    drawCircle(accent.copy(alpha = alpha), HANDLE_SIZE_PX / 2, centre, style = Stroke(HANDLE_BORDER_PX))
}
