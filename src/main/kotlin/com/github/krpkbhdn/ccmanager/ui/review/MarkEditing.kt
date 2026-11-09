package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.min

enum class Handle { BODY, NORTH, SOUTH, EAST, WEST, NORTH_EAST, NORTH_WEST, SOUTH_EAST, SOUTH_WEST, VERTEX }

data class HandleHit(val handle: Handle, val vertexIndex: Int = -1)

data class EdgeHit(val edgeIndex: Int, val x: Double, val y: Double)

/** Pure editing logic of the pointer tool; coordinates are normalised 0..1 unless named px. */
object MarkEditing {

    fun handleAt(mark: MarkDraft, nx: Double, ny: Double, imageW: Int, imageH: Int, tolerancePx: Double): HandleHit? = when (mark.kind) {
        MarkKind.PIN -> HandleHit(Handle.BODY).takeIf { distancePx(mark.x, mark.y, nx, ny, imageW, imageH) <= tolerancePx * PIN_TOLERANCE_FACTOR }
        MarkKind.FREEFORM -> vertexAt(mark, nx, ny, imageW, imageH, tolerancePx)
            ?: HandleHit(Handle.BODY).takeIf { MarkGeometry.insidePolygon(mark.points, nx, ny) }
        else -> resizeHandleAt(mark, nx, ny, imageW, imageH, tolerancePx)
            ?: HandleHit(Handle.BODY).takeIf { MarkGeometry.contains(mark, nx, ny, imageW.toDouble(), imageH.toDouble(), 0.0) }
    }

    fun move(mark: MarkDraft, dx: Double, dy: Double): MarkDraft {
        val mx = dx.coerceIn(-mark.x, 1.0 - mark.x - mark.w)
        val my = dy.coerceIn(-mark.y, 1.0 - mark.y - mark.h)
        return mark.copyWith(mark.x + mx, mark.y + my, mark.w, mark.h, mark.points.map { MarkPoint(it.x + mx, it.y + my) })
    }

    fun resize(mark: MarkDraft, handle: Handle, nx: Double, ny: Double, imageW: Int, imageH: Int): MarkDraft {
        if (mark.kind != MarkKind.RECTANGLE && mark.kind != MarkKind.ELLIPSE) return mark
        var left = mark.x
        var right = mark.x + mark.w
        var top = mark.y
        var bottom = mark.y + mark.h
        when (handle) {
            Handle.WEST, Handle.NORTH_WEST, Handle.SOUTH_WEST -> left = follow(nx, right, MIN_SIDE_PX / imageW)
            Handle.EAST, Handle.NORTH_EAST, Handle.SOUTH_EAST -> right = follow(nx, left, MIN_SIDE_PX / imageW)
            else -> Unit
        }
        when (handle) {
            Handle.NORTH, Handle.NORTH_WEST, Handle.NORTH_EAST -> top = follow(ny, bottom, MIN_SIDE_PX / imageH)
            Handle.SOUTH, Handle.SOUTH_WEST, Handle.SOUTH_EAST -> bottom = follow(ny, top, MIN_SIDE_PX / imageH)
            else -> Unit
        }
        return mark.copyWith(min(left, right), min(top, bottom), abs(right - left), abs(bottom - top), mark.points)
    }

    fun moveVertex(mark: MarkDraft, index: Int, nx: Double, ny: Double): MarkDraft {
        if (index !in mark.points.indices) return mark
        val points = mark.points.toMutableList()
        points[index] = MarkPoint(nx.coerceIn(0.0, 1.0), ny.coerceIn(0.0, 1.0))
        return withPoints(mark, points)
    }

    fun edgeHit(mark: MarkDraft, nx: Double, ny: Double, imageW: Int, imageH: Int, tolerancePx: Double): EdgeHit? {
        if (mark.kind != MarkKind.FREEFORM) return null
        val probe = MarkGeometry.nearestEdge(mark.points, nx, ny, imageW.toDouble(), imageH.toDouble())
            ?.takeIf { it.distancePx <= tolerancePx } ?: return null
        val nearVertex = mark.points.any { distancePx(it.x, it.y, probe.x, probe.y, imageW, imageH) <= tolerancePx }
        return if (nearVertex) null else EdgeHit(probe.edgeIndex, probe.x, probe.y)
    }

    fun insertVertex(mark: MarkDraft, edgeIndex: Int, x: Double, y: Double): MarkDraft {
        val points = mark.points.toMutableList()
        points.add(edgeIndex + 1, MarkPoint(x.coerceIn(0.0, 1.0), y.coerceIn(0.0, 1.0)))
        return withPoints(mark, points)
    }

    private fun resizeHandleAt(mark: MarkDraft, nx: Double, ny: Double, imageW: Int, imageH: Int, tolerancePx: Double): HandleHit? {
        val l = mark.x
        val r = mark.x + mark.w
        val t = mark.y
        val b = mark.y + mark.h
        val cx = l + mark.w / HALF
        val cy = t + mark.h / HALF
        val anchors = listOf(
            Triple(Handle.NORTH_WEST, l, t), Triple(Handle.NORTH_EAST, r, t),
            Triple(Handle.SOUTH_WEST, l, b), Triple(Handle.SOUTH_EAST, r, b),
            Triple(Handle.NORTH, cx, t), Triple(Handle.SOUTH, cx, b),
            Triple(Handle.WEST, l, cy), Triple(Handle.EAST, r, cy),
        )
        return anchors.map { it.first to distancePx(it.second, it.third, nx, ny, imageW, imageH) }
            .filter { it.second <= tolerancePx }
            .minByOrNull { it.second }?.let { HandleHit(it.first) }
    }

    private fun vertexAt(mark: MarkDraft, nx: Double, ny: Double, imageW: Int, imageH: Int, tolerancePx: Double): HandleHit? =
        mark.points.indices
            .map { it to distancePx(mark.points[it].x, mark.points[it].y, nx, ny, imageW, imageH) }
            .filter { it.second <= tolerancePx }
            .minByOrNull { it.second }?.let { HandleHit(Handle.VERTEX, it.first) }

    /** New position of a dragged side: follows the pointer, never closer than [minSide] to the fixed side. */
    private fun follow(pointer: Double, fixed: Double, minSide: Double): Double {
        val p = pointer.coerceIn(0.0, 1.0)
        if (abs(p - fixed) >= minSide) return p
        val preferred = if (p >= fixed) fixed + minSide else fixed - minSide
        return if (preferred in 0.0..1.0) preferred else 2 * fixed - preferred
    }

    private fun withPoints(mark: MarkDraft, points: List<MarkPoint>): MarkDraft {
        val minX = points.minOf { it.x }
        val minY = points.minOf { it.y }
        return mark.copyWith(minX, minY, points.maxOf { it.x } - minX, points.maxOf { it.y } - minY, points)
    }

    private fun MarkDraft.copyWith(x: Double, y: Double, w: Double, h: Double, points: List<MarkPoint>) =
        MarkDraft(kind, x, y, w, h, comment, points)

    private fun distancePx(ax: Double, ay: Double, bx: Double, by: Double, imageW: Int, imageH: Int) =
        hypot((ax - bx) * imageW, (ay - by) * imageH)
}
