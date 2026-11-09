package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test

class MarkEditingTest {
    private val size = 1000
    private val tol = 10.0

    private fun rect(x: Double = 0.2, y: Double = 0.2, w: Double = 0.4, h: Double = 0.4) = MarkDraft(MarkKind.RECTANGLE, x, y, w, h, "", emptyList())
    private fun ellipse() = MarkDraft(MarkKind.ELLIPSE, 0.2, 0.2, 0.4, 0.4, "", emptyList())
    private fun freeform(vararg p: Pair<Double, Double>): MarkDraft {
        val points = p.map { MarkPoint(it.first, it.second) }
        return MarkDraft(MarkKind.FREEFORM, points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x } - points.minOf { it.x },
            points.maxOf { it.y } - points.minOf { it.y }, "", points)
    }
    private fun triangle() = freeform(0.2 to 0.2, 0.8 to 0.2, 0.5 to 0.8)
    private fun lShape() = freeform(0.1 to 0.1, 0.9 to 0.1, 0.9 to 0.3, 0.3 to 0.3, 0.3 to 0.9, 0.1 to 0.9)

    private fun hit(mark: MarkDraft, x: Double, y: Double) = MarkEditing.handleAt(mark, x, y, size, size, tol)

    @Test
    fun `every resize handle of a rectangle is hit`() {
        assertThat(hit(rect(), 0.2, 0.2)).isEqualTo(HandleHit(Handle.NORTH_WEST))
        assertThat(hit(rect(), 0.6, 0.2)).isEqualTo(HandleHit(Handle.NORTH_EAST))
        assertThat(hit(rect(), 0.2, 0.6)).isEqualTo(HandleHit(Handle.SOUTH_WEST))
        assertThat(hit(rect(), 0.6, 0.6)).isEqualTo(HandleHit(Handle.SOUTH_EAST))
        assertThat(hit(rect(), 0.4, 0.2)).isEqualTo(HandleHit(Handle.NORTH))
        assertThat(hit(rect(), 0.4, 0.6)).isEqualTo(HandleHit(Handle.SOUTH))
        assertThat(hit(rect(), 0.2, 0.4)).isEqualTo(HandleHit(Handle.WEST))
        assertThat(hit(rect(), 0.6, 0.4)).isEqualTo(HandleHit(Handle.EAST))
    }

    @Test
    fun `a corner handle wins over the body and the centre is the body`() {
        assertThat(hit(rect(), 0.205, 0.205)).isEqualTo(HandleHit(Handle.NORTH_WEST))
        assertThat(hit(rect(), 0.4, 0.4)).isEqualTo(HandleHit(Handle.BODY))
        assertThat(hit(rect(), 0.9, 0.9)).isNull()
    }

    @Test
    fun `an ellipse has bounding box handles and an elliptic body`() {
        assertThat(hit(ellipse(), 0.2, 0.2)).isEqualTo(HandleHit(Handle.NORTH_WEST))
        assertThat(hit(ellipse(), 0.4, 0.4)).isEqualTo(HandleHit(Handle.BODY))
        assertThat(hit(ellipse(), 0.25, 0.25)).isNull()
    }

    @Test
    fun `a pin is a body hit within one and a half tolerances`() {
        val pin = MarkGeometry.pinAt(0.5f, 0.5f)
        assertThat(hit(pin, 0.514, 0.5)).isEqualTo(HandleHit(Handle.BODY))
        assertThat(hit(pin, 0.52, 0.5)).isNull()
    }

    @Test
    fun `a freeform hits the nearest vertex first then the body`() {
        assertThat(hit(triangle(), 0.805, 0.2)).isEqualTo(HandleHit(Handle.VERTEX, 1))
        assertThat(hit(triangle(), 0.5, 0.4)).isEqualTo(HandleHit(Handle.BODY))
        assertThat(hit(triangle(), 0.1, 0.9)).isNull()
    }

    @Test
    fun `a concave notch is not the body`() {
        assertThat(hit(lShape(), 0.2, 0.6)).isEqualTo(HandleHit(Handle.BODY))
        assertThat(hit(lShape(), 0.6, 0.6)).isNull()
    }

    @Test
    fun `move clamps the bounding box at every border`() {
        assertThat(MarkEditing.move(rect(), -0.5, 0.0).x).isEqualTo(0.0)
        assertThat(MarkEditing.move(rect(), 0.0, -0.5).y).isEqualTo(0.0)
        assertThat(MarkEditing.move(rect(), 0.9, 0.0).x).isCloseTo(0.6, within(1e-9))
        assertThat(MarkEditing.move(rect(), 0.0, 0.9).y).isCloseTo(0.6, within(1e-9))
    }

    @Test
    fun `move translates the points of a freeform`() {
        val moved = MarkEditing.move(triangle(), 0.1, 0.0)
        assertThat(moved.points[0].x).isCloseTo(0.3, within(1e-9))
        assertThat(moved.x).isCloseTo(0.3, within(1e-9))
    }

    @Test
    fun `move stops a freeform at the border with all points`() {
        val moved = MarkEditing.move(triangle(), 0.5, 0.0)
        assertThat(moved.x + moved.w).isCloseTo(1.0, within(1e-9))
        assertThat(moved.points[1].x).isCloseTo(1.0, within(1e-9))
    }

    @Test
    fun `each handle moves only its own sides`() {
        fun box(h: Handle, x: Double, y: Double) = MarkEditing.resize(rect(), h, x, y, size, size).let { listOf(it.x, it.y, it.w, it.h).map { v -> Math.round(v * 1e6) / 1e6 } }
        assertThat(box(Handle.EAST, 0.8, 0.9)).containsExactly(0.2, 0.2, 0.6, 0.4)
        assertThat(box(Handle.WEST, 0.1, 0.9)).containsExactly(0.1, 0.2, 0.5, 0.4)
        assertThat(box(Handle.NORTH, 0.9, 0.1)).containsExactly(0.2, 0.1, 0.4, 0.5)
        assertThat(box(Handle.SOUTH, 0.9, 0.8)).containsExactly(0.2, 0.2, 0.4, 0.6)
        assertThat(box(Handle.NORTH_WEST, 0.1, 0.1)).containsExactly(0.1, 0.1, 0.5, 0.5)
        assertThat(box(Handle.SOUTH_EAST, 0.8, 0.8)).containsExactly(0.2, 0.2, 0.6, 0.6)
        assertThat(box(Handle.NORTH_EAST, 0.8, 0.1)).containsExactly(0.2, 0.1, 0.6, 0.5)
        assertThat(box(Handle.SOUTH_WEST, 0.1, 0.8)).containsExactly(0.1, 0.2, 0.5, 0.6)
    }

    @Test
    fun `dragging past the opposite side flips the box`() {
        val flipped = MarkEditing.resize(rect(), Handle.WEST, 0.9, 0.4, size, size)
        assertThat(flipped.x).isCloseTo(0.6, within(1e-9))
        assertThat(flipped.w).isCloseTo(0.3, within(1e-9))
    }

    @Test
    fun `the pointer is clamped to the image and a box keeps four pixels`() {
        assertThat(MarkEditing.resize(rect(), Handle.EAST, 2.0, 0.4, size, size).w).isCloseTo(0.8, within(1e-9))
        assertThat(MarkEditing.resize(rect(), Handle.EAST, 0.2, 0.4, size, size).w).isCloseTo(0.004, within(1e-9))
    }

    @Test
    fun `resize ignores pins and freeforms`() {
        assertThat(MarkEditing.resize(triangle(), Handle.EAST, 0.9, 0.9, size, size)).isEqualTo(triangle())
    }

    @Test
    fun `moving a vertex clamps it and recomputes the bounding box`() {
        val moved = MarkEditing.moveVertex(triangle(), 2, 0.5, 1.5)
        assertThat(moved.points[2]).isEqualTo(MarkPoint(0.5, 1.0))
        assertThat(moved.y).isEqualTo(0.2)
        assertThat(moved.h).isCloseTo(0.8, within(1e-9))
    }

    @Test
    fun `an edge is offered near its middle but not near a vertex or far away`() {
        assertThat(MarkEditing.edgeHit(triangle(), 0.5, 0.205, size, size, tol)).isEqualTo(EdgeHit(0, 0.5, 0.2))
        assertThat(MarkEditing.edgeHit(triangle(), 0.205, 0.205, size, size, tol)).isNull()
        assertThat(MarkEditing.edgeHit(triangle(), 0.5, 0.5, size, size, tol)).isNull()
    }

    @Test
    fun `the closing edge is hit as the last edge`() {
        assertThat(MarkEditing.edgeHit(triangle(), 0.35, 0.5, size, size, tol)?.edgeIndex).isEqualTo(2)
    }

    @Test
    fun `a vertex is inserted after its edge start and appended for the closing edge`() {
        val inMiddle = MarkEditing.insertVertex(triangle(), 0, 0.5, 0.1)
        assertThat(inMiddle.points[1]).isEqualTo(MarkPoint(0.5, 0.1))
        assertThat(inMiddle.y).isEqualTo(0.1)
        val closing = MarkEditing.insertVertex(triangle(), 2, 0.35, 0.5)
        assertThat(closing.points).hasSize(4)
        assertThat(closing.points[3]).isEqualTo(MarkPoint(0.35, 0.5))
    }
}
