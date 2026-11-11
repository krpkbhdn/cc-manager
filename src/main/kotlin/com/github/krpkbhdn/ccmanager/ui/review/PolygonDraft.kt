package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import kotlin.math.hypot

private const val MIN_VERTICES = 3
private const val MIN_AREA_PX2 = 16.0

/** A freeform polygon being placed point by point, in normalised image coordinates so it survives zoom and pan; pixel arguments are image pixels. */
internal class PolygonDraft {
    var points by mutableStateOf(emptyList<MarkPoint>())
        private set

    val isEmpty: Boolean get() = points.isEmpty()

    /** Appends the point clamped to the image; false when it is closer than [minGapPx] to the previous one. */
    fun add(nx: Double, ny: Double, imageW: Float, imageH: Float, minGapPx: Float): Boolean {
        val next = MarkPoint(nx.coerceIn(0.0, 1.0), ny.coerceIn(0.0, 1.0))
        val last = points.lastOrNull()
        if (last != null && distancePx(last, next, imageW, imageH) < minGapPx) return false
        points = points + next
        return true
    }

    fun removeLast() {
        points = points.dropLast(1)
    }

    fun clear() {
        points = emptyList()
    }

    /** True when a click here would close the polygon: at least three points and within [tolerancePx] of the first. */
    fun closableAt(nx: Double, ny: Double, imageW: Float, imageH: Float, tolerancePx: Float): Boolean =
        points.size >= MIN_VERTICES && distancePx(points.first(), MarkPoint(nx, ny), imageW, imageH) <= tolerancePx

    /** True when [nx],[ny] is within [tolerancePx] of the first point, whatever the point count. */
    fun nearFirst(nx: Double, ny: Double, imageW: Float, imageH: Float, tolerancePx: Float): Boolean =
        points.isNotEmpty() && distancePx(points.first(), MarkPoint(nx, ny), imageW, imageH) <= tolerancePx

    /** The finished mark, or null with fewer than three points or a degenerate area; the draft is left as it is. */
    fun close(imageW: Float, imageH: Float): MarkDraft? {
        if (points.size < MIN_VERTICES) return null
        val pixels = points.map { MarkPoint(it.x * imageW, it.y * imageH) }
        if (MarkGeometry.polygonArea(pixels) < MIN_AREA_PX2) return null
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        return MarkDraft(MarkKind.FREEFORM, minX, minY, points.maxOf { it.x } - minX, points.maxOf { it.y } - minY, "", points)
    }

    private fun distancePx(a: MarkPoint, b: MarkPoint, imageW: Float, imageH: Float) = hypot((a.x - b.x) * imageW, (a.y - b.y) * imageH)
}
