package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal const val MIN_SIDE_PX = 4.0
internal const val HALF = 2.0
internal const val PIN_TOLERANCE_FACTOR = 1.5

/** Pure geometry for marks; inputs of the builders are image pixels, results and hit tests are normalised 0..1. */
object MarkGeometry {

    fun rectFromDrag(startX: Float, startY: Float, endX: Float, endY: Float, imageW: Float, imageH: Float): MarkDraft? =
        boxOf(MarkKind.RECTANGLE, min(startX, endX), min(startY, endY), max(startX, endX), max(startY, endY), imageW, imageH)

    fun ellipseFromStroke(points: List<Pair<Float, Float>>, imageW: Float, imageH: Float): MarkDraft? {
        if (points.isEmpty()) return null
        val xs = points.map { it.first }
        val ys = points.map { it.second }
        return boxOf(MarkKind.ELLIPSE, xs.min(), ys.min(), xs.max(), ys.max(), imageW, imageH)
    }

    fun pinAt(x: Float, y: Float): MarkDraft =
        MarkDraft(MarkKind.PIN, x.toDouble().coerceIn(0.0, 1.0), y.toDouble().coerceIn(0.0, 1.0), 0.0, 0.0, "", emptyList())

    /** Topmost mark under the point; on overlap the smallest area wins, then the later one. */
    fun hitTest(marks: List<MarkDraft>, nx: Float, ny: Float, imageW: Float, imageH: Float, tolerancePx: Float): Int? =
        marks.indices.reversed()
            .filter { contains(marks[it], nx.toDouble(), ny.toDouble(), imageW.toDouble(), imageH.toDouble(), tolerancePx.toDouble()) }
            .minByOrNull { areaOf(marks[it]) }

    fun labelOf(index: Int): String = (index + 1).toString()

    private fun boxOf(kind: MarkKind, left: Float, top: Float, right: Float, bottom: Float, imageW: Float, imageH: Float): MarkDraft? {
        val l = left.coerceIn(0f, imageW)
        val t = top.coerceIn(0f, imageH)
        val r = right.coerceIn(0f, imageW)
        val b = bottom.coerceIn(0f, imageH)
        if (r - l < MIN_SIDE_PX || b - t < MIN_SIDE_PX) return null
        return MarkDraft(kind, l / imageW.toDouble(), t / imageH.toDouble(), (r - l) / imageW.toDouble(), (b - t) / imageH.toDouble(), "", emptyList())
    }

    internal fun contains(mark: MarkDraft, px: Double, py: Double, imageW: Double, imageH: Double, tolerancePx: Double): Boolean = when (mark.kind) {
        MarkKind.PIN -> hypot((px - mark.x) * imageW, (py - mark.y) * imageH) <= tolerancePx * PIN_TOLERANCE_FACTOR
        MarkKind.RECTANGLE -> px in mark.x - tolerancePx / imageW..mark.x + mark.w + tolerancePx / imageW &&
            py in mark.y - tolerancePx / imageH..mark.y + mark.h + tolerancePx / imageH
        MarkKind.ELLIPSE -> insideEllipse(mark, px, py, tolerancePx / imageW, tolerancePx / imageH)
        MarkKind.FREEFORM -> insidePolygon(mark.points, px, py) ||
            (nearestEdge(mark.points, px, py, imageW, imageH)?.distancePx ?: Double.MAX_VALUE) <= tolerancePx
    }

    private fun insideEllipse(mark: MarkDraft, px: Double, py: Double, growX: Double, growY: Double): Boolean {
        val rx = mark.w / HALF + growX
        val ry = mark.h / HALF + growY
        if (mark.w <= 0 || mark.h <= 0) return false
        val cx = mark.x + mark.w / HALF
        val cy = mark.y + mark.h / HALF
        val dx = (px - cx) / rx
        val dy = (py - cy) / ry
        return dx * dx + dy * dy <= 1.0
    }

    private fun areaOf(mark: MarkDraft): Double =
        if (mark.kind == MarkKind.FREEFORM) polygonArea(mark.points) else mark.w * mark.h

    /** Shoelace area; units follow the points (normalised or pixels). */
    internal fun polygonArea(points: List<MarkPoint>): Double =
        abs(points.indices.sumOf { i -> val a = points[i]; val b = points[(i + 1) % points.size]; a.x * b.y - b.x * a.y }) / HALF

    /** Even-odd ray casting; the polygon is closed implicitly. */
    internal fun insidePolygon(points: List<MarkPoint>, px: Double, py: Double): Boolean {
        var inside = false
        var j = points.size - 1
        for (i in points.indices) {
            val a = points[i]
            val b = points[j]
            if ((a.y > py) != (b.y > py) && px < (b.x - a.x) * (py - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }

    internal class EdgeProbe(val edgeIndex: Int, val x: Double, val y: Double, val distancePx: Double)

    /** Nearest point on any polygon edge (including the closing one), distance in image pixels. */
    internal fun nearestEdge(points: List<MarkPoint>, px: Double, py: Double, imageW: Double, imageH: Double): EdgeProbe? =
        points.indices.map { probeEdge(it, points[it], points[(it + 1) % points.size], px, py, imageW, imageH) }
            .minByOrNull { it.distancePx }

    private fun probeEdge(index: Int, a: MarkPoint, b: MarkPoint, px: Double, py: Double, imageW: Double, imageH: Double): EdgeProbe {
        val dx = (b.x - a.x) * imageW
        val dy = (b.y - a.y) * imageH
        val lengthSq = dx * dx + dy * dy
        val t = if (lengthSq == 0.0) 0.0 else (((px - a.x) * imageW * dx + (py - a.y) * imageH * dy) / lengthSq).coerceIn(0.0, 1.0)
        val x = a.x + (b.x - a.x) * t
        val y = a.y + (b.y - a.y) * t
        return EdgeProbe(index, x, y, hypot((px - x) * imageW, (py - y) * imageH))
    }
}
