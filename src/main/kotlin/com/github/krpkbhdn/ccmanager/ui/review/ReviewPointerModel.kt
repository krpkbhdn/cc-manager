package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind

/** What a button press on the canvas starts. */
internal sealed interface PressPlan {
    data object Pan : PressPlan
    data class Draw(val tool: ReviewTool) : PressPlan
    data class Move(val index: Int) : PressPlan
    data class Resize(val index: Int, val handle: Handle) : PressPlan
    data class DragVertex(val index: Int, val vertex: Int) : PressPlan
    data class InsertVertex(val index: Int, val edge: EdgeHit) : PressPlan
    data object Deselect : PressPlan
}

internal enum class KeyAction { ARM_RECTANGLE, ARM_ELLIPSE, ARM_PIN, ARM_FREEFORM, DEFAULT_TOOL, CANCEL, DELETE, CLOSE_POLYGON }

/** Pure decisions of the canvas pointer: what a press starts, how a drag edits a mark, which key does what. */
internal object ReviewPointerModel {

    /**
     * An armed drawing tool always draws. With the POINTER the selected mark's handles, vertices and freeform edges win,
     * then the smallest mark under the point (the selected one included) is moved, so a mark nested in the selected one stays reachable.
     */
    fun press(
        tool: ReviewTool, panning: Boolean, marks: List<MarkDraft>, selected: Int?,
        nx: Float, ny: Float, imageW: Float, imageH: Float, tolerancePx: Float,
    ): PressPlan {
        if (panning) return PressPlan.Pan
        if (tool != ReviewTool.POINTER) return PressPlan.Draw(tool)
        val mark = selected?.let { marks.getOrNull(it) }
        if (selected != null && mark != null) {
            selectedPlan(selected, mark, nx, ny, imageW, imageH, tolerancePx)?.let { return it }
        }
        val hit = MarkGeometry.hitTest(marks, nx, ny, imageW, imageH, tolerancePx) ?: return PressPlan.Deselect
        return PressPlan.Move(hit)
    }

    private fun selectedPlan(index: Int, mark: MarkDraft, nx: Float, ny: Float, w: Float, h: Float, tol: Float): PressPlan? {
        val handle = MarkEditing.handleAt(mark, nx.toDouble(), ny.toDouble(), w.toInt(), h.toInt(), tol.toDouble())
        if (handle?.handle == Handle.VERTEX) return PressPlan.DragVertex(index, handle.vertexIndex)
        if (mark.kind == MarkKind.FREEFORM) {
            MarkEditing.edgeHit(mark, nx.toDouble(), ny.toDouble(), w.toInt(), h.toInt(), tol.toDouble())?.let { return PressPlan.InsertVertex(index, it) }
        }
        return handle?.takeIf { it.handle != Handle.BODY }?.let { PressPlan.Resize(index, it.handle) }
    }

    /** The mark as a drag from [startX],[startY] to [nx],[ny] leaves it, always computed from the [original] so clamping never accumulates. */
    fun edited(plan: PressPlan, original: MarkDraft, startX: Float, startY: Float, nx: Float, ny: Float, imageW: Float, imageH: Float): MarkDraft = when (plan) {
        is PressPlan.Move -> MarkEditing.move(original, (nx - startX).toDouble(), (ny - startY).toDouble())
        is PressPlan.Resize -> MarkEditing.resize(original, plan.handle, nx.toDouble(), ny.toDouble(), imageW.toInt(), imageH.toInt())
        is PressPlan.DragVertex -> MarkEditing.moveVertex(original, plan.vertex, nx.toDouble(), ny.toDouble())
        else -> original
    }

    fun inserted(plan: PressPlan.InsertVertex, original: MarkDraft): MarkDraft = MarkEditing.insertVertex(original, plan.edge.edgeIndex, plan.edge.x, plan.edge.y)

    /** With Ctrl, Meta or Shift held ([chorded]) only Esc stays ours, so Ctrl+V, Ctrl+F and the like reach whoever owns them. */
    fun keyAction(key: Key, chorded: Boolean = false): KeyAction? = when {
        key == Key.Escape -> KeyAction.CANCEL
        chorded -> null
        else -> when (key) {
            Key.R -> KeyAction.ARM_RECTANGLE
            Key.E -> KeyAction.ARM_ELLIPSE
            Key.P -> KeyAction.ARM_PIN
            Key.F -> KeyAction.ARM_FREEFORM
            Key.V -> KeyAction.DEFAULT_TOOL
            Key.Delete, Key.Backspace -> KeyAction.DELETE
            Key.Enter, Key.NumPadEnter -> KeyAction.CLOSE_POLYGON
            else -> null
        }
    }
}

/** Index of the mark a plan edits, null for plans that touch none. */
internal val PressPlan.markIndex: Int?
    get() = when (this) {
        is PressPlan.Move -> index
        is PressPlan.Resize -> index
        is PressPlan.DragVertex -> index
        is PressPlan.InsertVertex -> index
        else -> null
    }
