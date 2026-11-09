package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.review.MarkPoint
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewPointerModelTest {
    private val rect = MarkDraft(MarkKind.RECTANGLE, 0.2, 0.2, 0.4, 0.4, "")
    private val triangle = MarkDraft(MarkKind.FREEFORM, 0.2, 0.2, 0.4, 0.4, "", listOf(MarkPoint(0.2, 0.2), MarkPoint(0.6, 0.2), MarkPoint(0.4, 0.6)))

    private fun press(tool: ReviewTool, marks: List<MarkDraft>, selected: Int?, x: Float, y: Float, panning: Boolean = false) =
        ReviewPointerModel.press(tool, panning, marks, selected, x, y, 1000f, 1000f, 8f)

    @Test
    fun `ctrl drag or middle button starts a pan whatever the tool`() {
        assertThat(press(ReviewTool.RECTANGLE, listOf(rect), 0, 0.4f, 0.4f, panning = true)).isEqualTo(PressPlan.Pan)
    }

    @Test
    fun `a press on a resize handle of the selected mark starts a resize`() {
        assertThat(press(ReviewTool.POINTER, listOf(rect), 0, 0.6f, 0.6f)).isEqualTo(PressPlan.Resize(0, Handle.SOUTH_EAST))
    }

    @Test
    fun `a press on the body of the selected mark starts a move`() {
        assertThat(press(ReviewTool.POINTER, listOf(rect), 0, 0.4f, 0.4f)).isEqualTo(PressPlan.Move(0))
    }

    @Test
    fun `a press on an unselected mark selects it and starts a move`() {
        assertThat(press(ReviewTool.POINTER, listOf(rect), null, 0.4f, 0.4f)).isEqualTo(PressPlan.Move(0))
    }

    @Test
    fun `a press on empty space with the pointer deselects`() {
        assertThat(press(ReviewTool.POINTER, listOf(rect), 0, 0.9f, 0.9f)).isEqualTo(PressPlan.Deselect)
    }

    @Test
    fun `an armed drawing tool draws everywhere, also next to a handle`() {
        assertThat(press(ReviewTool.ELLIPSE, listOf(rect), 0, 0.9f, 0.9f)).isEqualTo(PressPlan.Draw(ReviewTool.ELLIPSE))
        assertThat(press(ReviewTool.ELLIPSE, listOf(rect), 0, 0.4f, 0.4f)).isEqualTo(PressPlan.Draw(ReviewTool.ELLIPSE))
        assertThat(press(ReviewTool.ELLIPSE, listOf(rect), 0, 0.6f, 0.6f)).isEqualTo(PressPlan.Draw(ReviewTool.ELLIPSE))
    }

    @Test
    fun `a press on a vertex of the selected freeform drags it`() {
        assertThat(press(ReviewTool.POINTER, listOf(triangle), 0, 0.6f, 0.2f)).isEqualTo(PressPlan.DragVertex(0, 1))
    }

    @Test
    fun `a press on an edge of the selected freeform inserts a vertex there`() {
        val plan = press(ReviewTool.POINTER, listOf(triangle), 0, 0.4f, 0.2f)

        assertThat(plan).isInstanceOf(PressPlan.InsertVertex::class.java)
        val inserted = ReviewPointerModel.inserted(plan as PressPlan.InsertVertex, triangle)
        assertThat(inserted.points).hasSize(4)
    }

    @Test
    fun `a move drag is computed from the original mark`() {
        val moved = ReviewPointerModel.edited(PressPlan.Move(0), rect, 0.4f, 0.4f, 0.5f, 0.4f, 1000f, 1000f)

        assertThat(moved.x).isEqualTo(0.3, org.assertj.core.data.Offset.offset(1e-6))
        assertThat(moved.y).isEqualTo(0.2, org.assertj.core.data.Offset.offset(1e-6))
    }

    @Test
    fun `the outline of a rectangle or ellipse moves it, selected or not, away from the handles`() {
        val ellipse = MarkDraft(MarkKind.ELLIPSE, 0.2, 0.2, 0.4, 0.4, "")
        listOf(null, 0).forEach { selected ->
            listOf(0.195f, 0.205f).forEach { x -> assertThat(press(ReviewTool.POINTER, listOf(rect), selected, x, 0.3f)).isEqualTo(PressPlan.Move(0)) }
            listOf(0.2551f, 0.2621f).forEach { v -> assertThat(press(ReviewTool.POINTER, listOf(ellipse), selected, v, v)).isEqualTo(PressPlan.Move(0)) }
        }
    }

    @Test
    fun `an unselected freeform is moved by its outline, the selected one inserts a vertex there`() {
        assertThat(press(ReviewTool.POINTER, listOf(triangle), null, 0.4f, 0.195f)).isEqualTo(PressPlan.Move(0))
        assertThat(press(ReviewTool.POINTER, listOf(triangle), 0, 0.4f, 0.4f)).isEqualTo(PressPlan.Move(0))
    }

    @Test
    fun `keys map to actions`() {
        assertThat(ReviewPointerModel.keyAction(Key.R)).isEqualTo(KeyAction.ARM_RECTANGLE)
        assertThat(ReviewPointerModel.keyAction(Key.Delete)).isEqualTo(KeyAction.DELETE)
        assertThat(ReviewPointerModel.keyAction(Key.Escape)).isEqualTo(KeyAction.CANCEL)
        assertThat(ReviewPointerModel.keyAction(Key.Q)).isNull()
    }

    private val inner = MarkDraft(MarkKind.RECTANGLE, 0.3, 0.3, 0.1, 0.1, "")

    @Test
    fun `a mark nested in the selected one is pressed, not the selected body`() {
        assertThat(press(ReviewTool.POINTER, listOf(rect, inner), 0, 0.35f, 0.35f)).isEqualTo(PressPlan.Move(1))
        assertThat(press(ReviewTool.POINTER, listOf(rect, inner), 0, 0.5f, 0.5f)).isEqualTo(PressPlan.Move(0))
    }

    @Test
    fun `keys with ctrl meta or shift are left alone except escape`() {
        assertThat(ReviewPointerModel.keyAction(Key.V, chorded = true)).isNull()
        assertThat(ReviewPointerModel.keyAction(Key.Delete, chorded = true)).isNull()
        assertThat(ReviewPointerModel.keyAction(Key.Escape, chorded = true)).isEqualTo(KeyAction.CANCEL)
    }

    private fun gesture(plan: PressPlan, marks: List<MarkDraft>) = MarkGesture(plan, marks, 0.4f, 0.4f)
    private fun MarkGesture.dragTo(marks: List<MarkDraft>, x: Float) = also { track(10f) }.drag(marks, x, 0.4f, 1000f, 1000f)

    @Test
    fun `a gesture writes nothing before the pointer left the slop`() {
        val gesture = gesture(PressPlan.Move(0), listOf(rect))

        assertThat(gesture.track(MOVE_SLOP_PX)).isFalse()
        assertThat(gesture.drag(listOf(rect), 0.5f, 0.4f, 1000f, 1000f)).isNull()
        assertThat(gesture.track(MOVE_SLOP_PX + 1)).isTrue()
    }

    @Test
    fun `a gesture gives up when its mark was deleted and another shifted into the slot`() {
        val other = MarkDraft(MarkKind.RECTANGLE, 0.7, 0.7, 0.1, 0.1, "")
        val gesture = gesture(PressPlan.Move(0), listOf(rect, other))
        val first = gesture.dragTo(listOf(rect, other), 0.5f)!!

        assertThat(gesture.drag(listOf(first.mark, other), 0.6f, 0.4f, 1000f, 1000f)).isNotNull()
        assertThat(gesture.drag(listOf(other), 0.7f, 0.4f, 1000f, 1000f)).isNull()
        assertThat(gesture.ended).isTrue()
        assertThat(gesture.cancel(listOf(other))).isNull()
    }

    @Test
    fun `cancelling an edit reverts the mark to its original once`() {
        val gesture = gesture(PressPlan.Move(0), listOf(rect))
        val moved = gesture.dragTo(listOf(rect), 0.5f)!!.mark

        val revert = gesture.cancel(listOf(moved))!!

        assertThat(revert.mark).isEqualTo(rect)
        assertThat(gesture.dragTo(listOf(rect), 0.6f)).isNull()
        assertThat(gesture.cancel(listOf(rect))).isNull()
    }

    @Test
    fun `cancelling before any write reverts nothing`() {
        assertThat(gesture(PressPlan.Move(0), listOf(rect)).cancel(listOf(rect))).isNull()
        assertThat(gesture(PressPlan.Draw(ReviewTool.RECTANGLE), listOf(rect)).cancel(listOf(rect))).isNull()
    }

    @Test
    fun `a freeform edge gets its vertex only when the pointer moves and cancel removes it again`() {
        val plan = press(ReviewTool.POINTER, listOf(triangle), 0, 0.4f, 0.2f) as PressPlan.InsertVertex
        val gesture = gesture(plan, listOf(triangle))

        assertThat(gesture.drag(listOf(triangle), 0.4f, 0.3f, 1000f, 1000f)).isNull()
        val dragged = gesture.dragTo(listOf(triangle), 0.4f)!!
        assertThat(dragged.mark.points).hasSize(4)
        assertThat(gesture.plan).isEqualTo(PressPlan.DragVertex(0, 1))
        assertThat(gesture.cancel(listOf(dragged.mark))!!.mark).isEqualTo(triangle)
    }
}
