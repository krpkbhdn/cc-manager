package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.data.Offset.offset
import org.junit.jupiter.api.Test

class ReviewMarkGesturesTest {
    private val imageW = 1000f
    private val imageH = 500f

    /** A 1032x532 pane fits the 1000x500 image at scale 1 with a 16 px margin, so screen = image + 16. */
    private val viewer = ReviewViewerState(imageW, imageH).also { it.resize(IntSize(1032, 532)) }
    private val viewport = viewer.viewport!!

    private fun rect(x: Double, y: Double, w: Double, h: Double) = MarkDraft(MarkKind.RECTANGLE, x, y, w, h, "")

    private fun screen(imageX: Float, imageY: Float) = Offset(imageX + 16f, imageY + 16f)

    @Test
    fun `the fitted viewport maps image pixels to screen with the margin`() {
        assertThat(viewport.scale).isEqualTo(1f)
        assertThat(normalised(viewport, viewer, screen(500f, 250f))).isEqualTo(0.5f to 0.5f)
    }

    @Test
    fun `a rectangle drag becomes a normalised rectangle`() {
        val points = listOf(screen(100f, 50f), screen(200f, 100f), screen(300f, 150f))
        assertThat(draftOf(ReviewTool.RECTANGLE, points, viewport, imageW, imageH)).isEqualTo(rect(0.1, 0.1, 0.2, 0.2))
    }

    @Test
    fun `a zero-size rectangle drag creates nothing`() {
        val point = screen(100f, 100f)
        assertThat(draftOf(ReviewTool.RECTANGLE, listOf(point, point), viewport, imageW, imageH)).isNull()
    }

    @Test
    fun `an ellipse stroke becomes an ellipse over its bounding box`() {
        val points = listOf(screen(100f, 100f), screen(300f, 120f), screen(200f, 200f))
        val mark = draftOf(ReviewTool.ELLIPSE, points, viewport, imageW, imageH)!!
        assertThat(mark.kind).isEqualTo(MarkKind.ELLIPSE)
        assertThat(mark.x).isCloseTo(0.1, offset(1e-6))
        assertThat(mark.w).isCloseTo(0.2, offset(1e-6))
        assertThat(mark.h).isCloseTo(0.2, offset(1e-6))
    }

    @Test
    fun `tools that do not draw by dragging give no draft`() {
        val points = listOf(screen(100f, 100f), screen(300f, 300f))
        assertThat(draftOf(ReviewTool.PIN, points, viewport, imageW, imageH)).isNull()
        assertThat(draftOf(ReviewTool.FREEFORM, points, viewport, imageW, imageH)).isNull()
        assertThat(draftOf(ReviewTool.POINTER, points, viewport, imageW, imageH)).isNull()
    }

    @Test
    fun `a drag is mapped through zoom and pan`() {
        val zoomed = Viewport(2f, 10f, 20f)
        val points = listOf(Offset(10f + 2 * 100f, 20f + 2 * 50f), Offset(10f + 2 * 300f, 20f + 2 * 150f))
        assertThat(draftOf(ReviewTool.RECTANGLE, points, zoomed, imageW, imageH)).isEqualTo(rect(0.1, 0.1, 0.2, 0.2))
    }

    @Test
    fun `a pin is placed at the press point`() {
        val (x, y) = normalised(viewport, viewer, screen(250f, 125f))
        val pin = MarkGeometry.pinAt(x, y)
        assertThat(pin.kind).isEqualTo(MarkKind.PIN)
        assertThat(pin.x).isCloseTo(0.25, offset(1e-6))
        assertThat(pin.y).isCloseTo(0.25, offset(1e-6))
    }

    @Test
    fun `freeform points are placed one by one and the first point closes the polygon`() {
        val draft = PolygonDraft()
        val tolerance = HIT_TOLERANCE_PX
        draft.add(0.1, 0.1, imageW, imageH, 2f)
        draft.add(0.5, 0.1, imageW, imageH, 2f)
        draft.add(0.5, 0.6, imageW, imageH, 2f)
        assertThat(draft.closableAt(0.1, 0.1, imageW, imageH, tolerance)).isTrue()
        assertThat(draft.closableAt(0.9, 0.9, imageW, imageH, tolerance)).isFalse()
        val mark = draft.close(imageW, imageH)!!
        assertThat(mark.kind).isEqualTo(MarkKind.FREEFORM)
        assertThat(mark.points).hasSize(3)
    }

    @Test
    fun `a freeform polygon with fewer than three points does not close`() {
        val draft = PolygonDraft()
        draft.add(0.1, 0.1, imageW, imageH, 2f)
        draft.add(0.5, 0.5, imageW, imageH, 2f)
        assertThat(draft.closableAt(0.1, 0.1, imageW, imageH, HIT_TOLERANCE_PX)).isFalse()
        assertThat(draft.close(imageW, imageH)).isNull()
    }

    @Test
    fun `a double click on the same freeform point adds it once`() {
        val draft = PolygonDraft()
        assertThat(draft.add(0.2, 0.2, imageW, imageH, 2f)).isTrue()
        assertThat(draft.add(0.2, 0.2, imageW, imageH, 2f)).isFalse()
        assertThat(draft.points).hasSize(1)
    }

    @Test
    fun `a press on empty space deselects and a click there hits no mark`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2))
        val plan = ReviewPointerModel.press(ReviewTool.POINTER, false, marks, null, 0.8f, 0.8f, imageW, imageH, HIT_TOLERANCE_PX)
        assertThat(plan).isEqualTo(PressPlan.Deselect)
        assertThat(hitMark(marks, screen(800f, 400f), viewport, viewer)).isNull()
    }

    @Test
    fun `hitMark finds the mark under a screen point`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2), MarkGeometry.pinAt(0.8f, 0.8f))
        assertThat(hitMark(marks, screen(200f, 100f), viewport, viewer)).isEqualTo(0)
        assertThat(hitMark(marks, screen(800f, 400f), viewport, viewer)).isEqualTo(1)
    }

    @Test
    fun `an armed drawing tool always draws and panning wins over it`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2))
        assertThat(ReviewPointerModel.press(ReviewTool.RECTANGLE, false, marks, null, 0.15f, 0.15f, imageW, imageH, HIT_TOLERANCE_PX))
            .isEqualTo(PressPlan.Draw(ReviewTool.RECTANGLE))
        assertThat(ReviewPointerModel.press(ReviewTool.RECTANGLE, true, marks, null, 0.15f, 0.15f, imageW, imageH, HIT_TOLERANCE_PX))
            .isEqualTo(PressPlan.Pan)
    }

    @Test
    fun `a press on a mark plans a move and a drag moves it by the pointer distance`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2))
        val plan = ReviewPointerModel.press(ReviewTool.POINTER, false, marks, null, 0.2f, 0.2f, imageW, imageH, HIT_TOLERANCE_PX)
        assertThat(plan).isEqualTo(PressPlan.Move(0))
        val gesture = MarkGesture(plan, marks, 0.2f, 0.2f)
        assertThat(gesture.track(50f)).isTrue()
        val edit = gesture.drag(marks, 0.3f, 0.2f, imageW, imageH)!!
        assertThat(edit.index).isEqualTo(0)
        assertThat(edit.mark.x).isCloseTo(0.2, offset(1e-6))
        assertThat(edit.mark.y).isCloseTo(0.1, offset(1e-6))
        assertThat(edit.mark.w).isCloseTo(0.2, offset(1e-6))
    }

    @Test
    fun `a press that never leaves the slop edits nothing`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2))
        val gesture = MarkGesture(PressPlan.Move(0), marks, 0.2f, 0.2f)
        assertThat(gesture.track(1f)).isFalse()
        assertThat(gesture.drag(marks, 0.21f, 0.2f, imageW, imageH)).isNull()
        assertThat(gesture.cancel(marks)).isNull()
    }

    @Test
    fun `dragging an east handle widens the mark from its original`() {
        val marks = listOf(rect(0.1, 0.1, 0.2, 0.2))
        val gesture = MarkGesture(PressPlan.Resize(0, Handle.EAST), marks, 0.3f, 0.2f)
        gesture.track(50f)
        val edit = gesture.drag(marks, 0.5f, 0.2f, imageW, imageH)!!
        assertThat(edit.mark.x).isCloseTo(0.1, offset(1e-6))
        assertThat(edit.mark.w).isCloseTo(0.4, offset(1e-6))
        assertThat(edit.mark.h).isCloseTo(0.2, offset(1e-6))
    }

    @Test
    fun `cancelling a move after it wrote restores the original mark`() {
        val original = rect(0.1, 0.1, 0.2, 0.2)
        val gesture = MarkGesture(PressPlan.Move(0), listOf(original), 0.2f, 0.2f)
        gesture.track(50f)
        val moved = gesture.drag(listOf(original), 0.3f, 0.2f, imageW, imageH)!!
        val restore = gesture.cancel(listOf(moved.mark))!!
        assertThat(restore.mark).isEqualTo(original)
        assertThat(gesture.ended).isTrue()
    }

    @Test
    fun `a gesture gives up when the mark list changed under it`() {
        val original = rect(0.1, 0.1, 0.2, 0.2)
        val gesture = MarkGesture(PressPlan.Move(0), listOf(original), 0.2f, 0.2f)
        gesture.track(50f)
        gesture.drag(listOf(original), 0.3f, 0.2f, imageW, imageH)
        assertThat(gesture.drag(emptyList(), 0.4f, 0.2f, imageW, imageH)).isNull()
        assertThat(gesture.ended).isTrue()
        assertThat(gesture.cancel(emptyList())).isNull()
    }

    @Test
    fun `a gesture on a missing mark index edits nothing`() {
        val gesture = MarkGesture(PressPlan.Move(3), emptyList(), 0.2f, 0.2f)
        gesture.track(50f)
        assertThat(gesture.drag(emptyList(), 0.3f, 0.2f, imageW, imageH)).isNull()
    }

    @Test
    fun `the active gesture aborts once`() {
        val active = ActiveGesture()
        var aborted = 0
        active.begin { aborted++ }
        assertThat(active.running).isTrue()
        assertThat(active.cancel()).isTrue()
        assertThat(active.cancel()).isFalse()
        assertThat(aborted).isEqualTo(1)
        assertThat(active.running).isFalse()
    }
}
