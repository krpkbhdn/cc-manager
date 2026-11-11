package com.github.krpkbhdn.ccmanager.ui.review

import com.github.krpkbhdn.ccmanager.review.MarkDraft

/** Screen pixels the pointer must travel from the press before a press becomes a drag. */
internal const val MOVE_SLOP_PX = 3f

internal class MarkEdit(val index: Int, val mark: MarkDraft)

/**
 * One pointer gesture from press to release, pure. It edits the mark it captured at press from that original (so clamping never accumulates)
 * and gives up when the list no longer holds what it last wrote (the mark was deleted or the list changed), so it never writes into a shifted slot.
 */
internal class MarkGesture(plan: PressPlan, marks: List<MarkDraft>, private val startX: Float, private val startY: Float) {
    var plan: PressPlan = plan
        private set
    private val index: Int? = plan.markIndex
    private val before: MarkDraft? = index?.let(marks::getOrNull)
    private var base: MarkDraft? = before
    private var written: MarkDraft? = before

    /** True once the pointer left the slop around the press; stays true. */
    var moved = false
        private set

    /** Cancelled, aborted or finished: further pointer moves are ignored. */
    var ended = false
        private set

    fun track(distancePx: Float): Boolean {
        moved = moved || distancePx > MOVE_SLOP_PX
        return moved
    }

    private fun intact(marks: List<MarkDraft>): Boolean = index == null || (written != null && marks.getOrNull(index) == written)

    /** The next geometry of the edited mark; a pressed freeform edge gets its vertex only now, at the press point. */
    fun drag(marks: List<MarkDraft>, nx: Float, ny: Float, imageW: Float, imageH: Float): MarkEdit? {
        val target = index ?: return null
        if (ended || !moved) return null
        if (!intact(marks)) {
            ended = true
            return null
        }
        var original = base ?: return null
        (plan as? PressPlan.InsertVertex)?.let { insert ->
            original = ReviewPointerModel.inserted(insert, original)
            base = original
            plan = PressPlan.DragVertex(target, insert.edge.edgeIndex + 1)
        }
        val edited = ReviewPointerModel.edited(plan, original, startX, startY, nx, ny, imageW, imageH)
        written = edited
        return MarkEdit(target, edited)
    }

    /** Ends the gesture; the edit that puts the original mark back, or null when nothing was written or the list changed meanwhile. */
    fun cancel(marks: List<MarkDraft>): MarkEdit? {
        if (ended) return null
        val target = index
        val original = before
        ended = true
        if (target == null || original == null || written == original || !intact(marks)) return null
        return MarkEdit(target, original)
    }
}

/** The gesture in progress, so a key press or lost focus can cancel it from outside the pointer loop. */
internal class ActiveGesture {
    private var abort: (() -> Unit)? = null

    val running: Boolean get() = abort != null

    fun begin(onAbort: () -> Unit) {
        abort = onAbort
    }

    fun end() {
        abort = null
    }

    /** Aborts the running gesture once; false when none runs. */
    fun cancel(): Boolean {
        val running = abort ?: return false
        abort = null
        running()
        return true
    }
}
