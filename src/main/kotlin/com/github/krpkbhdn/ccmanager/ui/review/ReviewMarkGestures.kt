package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.drag
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerKeyboardModifiers
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isMetaPressed
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.isTertiaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import com.github.krpkbhdn.ccmanager.review.MarkDraft

internal const val HIT_TOLERANCE_PX = 10f

/** A polygon click closer than this to the previous point is a double click, not a new point. */
private const val MIN_POINT_GAP_PX = 2f

/** What the canvas gestures read and report; the reads are lambdas so a gesture always sees the latest state. */
internal class CanvasActions(
    val tool: () -> ReviewTool,
    val marks: () -> List<MarkDraft>,
    val selected: () -> Int?,
    val onPreview: (MarkDraft?) -> Unit,
    val onAddMark: (MarkDraft) -> Unit,
    val onSelect: (Int?) -> Unit,
    val onEdit: (Int, MarkDraft) -> Unit,
    val onContextMenu: (Offset) -> Unit,
    val onDiscardDraw: () -> Unit,
    val onPanning: (Boolean) -> Unit,
    val draft: PolygonDraft,
    val onCloseDraft: () -> Boolean,
    val onCancelDraft: () -> Unit,
    val active: ActiveGesture,
)

/** The mark a drag with [tool] would create, or null while it is too small or the tool does not draw by dragging. */
internal fun draftOf(tool: ReviewTool, points: List<Offset>, viewport: Viewport, imageW: Float, imageH: Float): MarkDraft? {
    val image = points.map { viewport.toImage(it.x, it.y) }
    return when (tool) {
        ReviewTool.RECTANGLE -> MarkGeometry.rectFromDrag(image.first().first, image.first().second, image.last().first, image.last().second, imageW, imageH)
        ReviewTool.ELLIPSE -> MarkGeometry.ellipseFromStroke(image, imageW, imageH)
        else -> null
    }
}

/** Wheel zoom around the cursor. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.wheelZoom(viewer: ReviewViewerState): Modifier = onPointerEvent(PointerEventType.Scroll) { event ->
    val change = event.changes.first()
    viewer.zoomAt(change.position.x, change.position.y, Viewport.zoomFactor(change.scrollDelta.y))
    change.consume()
}

/** Reports the pointer position (null when it leaves), for the cursor and the ghost vertex. */
@OptIn(ExperimentalComposeUiApi::class)
internal fun Modifier.hoverTracking(onHover: (Offset?) -> Unit): Modifier =
    onPointerEvent(PointerEventType.Move) { onHover(it.changes.first().position) }
        .onPointerEvent(PointerEventType.Enter) { onHover(it.changes.first().position) }
        .onPointerEvent(PointerEventType.Exit) { onHover(null) }

/** Ctrl pans on Windows and Linux; on macOS Ctrl+click is a right click, so Cmd does. */
private val panModifierIsMeta = System.getProperty("os.name").orEmpty().contains("mac", ignoreCase = true)

internal fun PointerKeyboardModifiers.isPanModifier(meta: Boolean = panModifierIsMeta): Boolean = if (meta) isMetaPressed else isCtrlPressed

/** Press starts what [ReviewPointerModel.press] plans: pan (Ctrl, Cmd on macOS, with the left button, or the middle button), draw, move/resize/vertex edit of a mark, select; right button opens the context menu. */
internal fun Modifier.markGestures(viewer: ReviewViewerState, actions: CanvasActions): Modifier = pointerInput(viewer) {
    awaitEachGesture {
        val down = awaitAnyButtonDown()
        val viewport = viewer.viewport ?: return@awaitEachGesture
        if (currentEvent.buttons.isSecondaryPressed) {
            if (actions.draft.isEmpty) actions.onContextMenu(down.position) else actions.onCancelDraft()
            down.consume()
            return@awaitEachGesture
        }
        val panning = currentEvent.keyboardModifiers.isPanModifier() || currentEvent.buttons.isTertiaryPressed
        val run = GestureRun(viewer, actions, down.position, viewport, panning)
        try {
            val completed = drag(down.id) { change ->
                if (currentEvent.buttons.isSecondaryPressed) actions.active.cancel()
                run.drag(change)
                change.consume()
            }
            run.finish(completed && Rect(Offset.Zero, size.toSize()).contains(run.last))
        } finally {
            actions.onPanning(false)
        }
    }
}

/** awaitFirstDown only reports primary-button presses, so right and middle presses (context menu, pan) never reached the gesture. */
private suspend fun AwaitPointerEventScope.awaitAnyButtonDown(): PointerInputChange {
    while (true) {
        awaitPointerEvent().changes.firstOrNull { it.changedToDown() }?.let { return it }
    }
}

/** What one pointer gesture does on the canvas; the mark edits themselves are decided by [MarkGesture]. */
private class GestureRun(
    private val viewer: ReviewViewerState,
    private val actions: CanvasActions,
    private val start: Offset,
    viewport: Viewport,
    panning: Boolean,
) {
    private val points = mutableListOf(start)
    private val gesture: MarkGesture
    var last = start
        private set

    init {
        val (x, y) = normalised(viewport, viewer, start)
        val plan = ReviewPointerModel.press(actions.tool(), panning, actions.marks(), actions.selected(), x, y, viewer.imageW, viewer.imageH, HIT_TOLERANCE_PX / viewport.scale)
        gesture = MarkGesture(plan, actions.marks(), x, y)
        when (plan) {
            PressPlan.Deselect -> actions.onSelect(null)
            is PressPlan.Move -> actions.onSelect(plan.index)
            is PressPlan.Draw -> if (plan.tool == ReviewTool.FREEFORM) placePolygonPoint(x, y, viewport.scale)
            else -> Unit
        }
        actions.active.begin(::abort)
    }

    /** A polygon point is placed by the press itself; a press on the first point closes the polygon. */
    private fun placePolygonPoint(x: Float, y: Float, scale: Float) {
        val (w, h) = viewer.imageW to viewer.imageH
        val draft = actions.draft
        when {
            draft.closableAt(x.toDouble(), y.toDouble(), w, h, HIT_TOLERANCE_PX / scale) -> actions.onCloseDraft()
            !draft.nearFirst(x.toDouble(), y.toDouble(), w, h, HIT_TOLERANCE_PX / scale) -> draft.add(x.toDouble(), y.toDouble(), w, h, MIN_POINT_GAP_PX / scale)
        }
    }

    fun drag(change: PointerInputChange) {
        last = change.position
        val viewport = viewer.viewport
        if (gesture.ended || viewport == null) return
        val moved = gesture.track((change.position - start).getDistance())
        when (val plan = gesture.plan) {
            PressPlan.Pan -> if (moved) {
                actions.onPanning(true)
                change.positionChange().let { viewer.panBy(it.x, it.y) }
            }
            is PressPlan.Draw -> if (plan.tool != ReviewTool.FREEFORM) {
                points += change.position
                actions.onPreview(if (moved) draftOf(plan.tool, points, viewport, viewer.imageW, viewer.imageH) else null)
            }
            else -> editMark(viewport, change.position)
        }
    }

    private fun editMark(viewport: Viewport, at: Offset) {
        val (x, y) = normalised(viewport, viewer, at)
        gesture.drag(actions.marks(), x, y, viewer.imageW, viewer.imageH)?.let { actions.onEdit(it.index, it.mark) }
    }

    /** Ends the gesture on release: places a drawn mark or pin, or treats an unmoved press as a click. */
    fun finish(released: Boolean) {
        actions.active.end()
        actions.onPreview(null)
        if (gesture.ended) return
        if (!released) return abort()
        when (val plan = gesture.plan) {
            is PressPlan.Draw -> finishDraw(plan.tool)
            is PressPlan.InsertVertex -> selectAtStart()
            else -> Unit
        }
    }

    /** Draw: nothing is created and the one-shot is dropped; an edit: the mark goes back to what it was at the press. */
    private fun abort() {
        gesture.cancel(actions.marks())?.let { actions.onEdit(it.index, it.mark) }
        actions.onPreview(null)
        if ((gesture.plan as? PressPlan.Draw)?.tool?.let { it != ReviewTool.FREEFORM } == true) actions.onDiscardDraw()
    }

    private fun finishDraw(tool: ReviewTool) {
        if (tool == ReviewTool.FREEFORM) return
        val viewport = viewer.viewport ?: return
        if (tool == ReviewTool.PIN) return normalised(viewport, viewer, start).let { (x, y) -> actions.onAddMark(MarkGeometry.pinAt(x, y)) }
        val created = if (gesture.moved) draftOf(tool, points, viewport, viewer.imageW, viewer.imageH) else null
        when {
            created != null -> actions.onAddMark(created)
            !gesture.moved -> selectAtStart()
        }
    }

    private fun selectAtStart() {
        val viewport = viewer.viewport ?: return
        actions.onSelect(hitMark(actions.marks(), start, viewport, viewer))
    }
}

/** Normalised (0..1) image position of a screen point. */
internal fun normalised(viewport: Viewport, viewer: ReviewViewerState, at: Offset): Pair<Float, Float> =
    viewport.toImage(at.x, at.y).let { (x, y) -> Pair(x / viewer.imageW, y / viewer.imageH) }

internal fun hitMark(marks: List<MarkDraft>, at: Offset, viewport: Viewport, viewer: ReviewViewerState): Int? {
    val (x, y) = normalised(viewport, viewer, at)
    return MarkGeometry.hitTest(marks, x, y, viewer.imageW, viewer.imageH, HIT_TOLERANCE_PX / viewport.scale)
}
