package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlin.math.roundToInt

private const val FREEFORM_HINT = "Freeform: click to add points, click the first point to close. Backspace removes the last point, Esc cancels"

private class OpenMenu(val at: Offset, val markIndex: Int?)

/**
 * The screenshot under the viewer's zoom and pan with its marks; [bitmap] null shows a spinner while the file loads.
 * A click gives the canvas keyboard focus, so letter hotkeys and Delete reach it but never a focused comment field.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun ReviewImageView(viewer: ReviewViewerState, bitmap: ImageBitmap?, imageId: Long, state: ReviewState, modifier: Modifier = Modifier) = key(viewer) {
    var preview by remember { mutableStateOf<MarkDraft?>(null) }
    var hover by remember { mutableStateOf<Offset?>(null) }
    var menu by remember { mutableStateOf<OpenMenu?>(null) }
    var panning by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    val measurer = rememberTextMeasurer()
    val marks = state.marks(imageId)
    val active = remember(viewer) { ActiveGesture() }
    val actions = remember(viewer, imageId, state) {
        CanvasActions(
            tool = { state.effectiveTool }, marks = { state.marks(imageId) }, selected = { state.selectedMark },
            onPreview = { preview = it }, onAddMark = { state.addMark(imageId, it) }, onSelect = { state.selectedMark = it },
            onEdit = { index, edited -> state.updateMark(imageId, index, edited) },
            onContextMenu = { at -> menu = OpenMenu(at, viewer.viewport?.let { hitMark(state.marks(imageId), at, it, viewer) }) },
            onDiscardDraw = state::clearOneShot, onPanning = { panning = it }, draft = state.draft,
            onCloseDraft = { state.closeDraft(imageId, viewer.imageW, viewer.imageH) }, onCancelDraft = state::clearOneShot, active = active,
        )
    }
    DisposableEffect(viewer) { onDispose { state.draft.clear() } }
    val plan = hover?.let { planAt(it, viewer, state, marks) }
    Box(
        modifier.fillMaxSize().clipToBounds().background(colors.terminal)
            .onSizeChanged { viewer.resize(it) }
            .focusRequester(focus).onFocusChanged { if (!it.isFocused) active.cancel() }.focusable()
            .onPreviewKeyEvent { handleKey(it, state, imageId, viewer, active) }
            .onPointerEvent(PointerEventType.Press) { focus.requestFocus() }
            .wheelZoom(viewer)
            .hoverTracking { hover = it }
            .markGestures(viewer, actions)
            .pointerHoverIcon(pointerIconOf(cursorFor(plan, state.selectedMark, panning))),
        contentAlignment = Alignment.Center,
    ) {
        val viewport = viewer.viewport
        if (bitmap == null) {
            Spinner()
        } else if (viewport != null) {
            val accent = colors.accent
            val ghost = (plan as? PressPlan.InsertVertex)?.edge
            Canvas(Modifier.fillMaxSize()) {
                val shown = IntSize((viewer.imageW * viewport.scale).roundToInt(), (viewer.imageH * viewport.scale).roundToInt())
                drawImage(bitmap, dstOffset = IntOffset(viewport.offsetX.roundToInt(), viewport.offsetY.roundToInt()), dstSize = shown, filterQuality = FilterQuality.Medium)
                marks.forEachIndexed { index, mark -> drawMark(mark, index, index == state.selectedMark, viewport, viewer.imageW, viewer.imageH, measurer) }
                state.selectedMark?.let { marks.getOrNull(it) }?.let { drawHandles(it, viewport, viewer.imageW, viewer.imageH, accent) }
                ghost?.let { drawGhostVertex(screenPoint(it.x, it.y, viewport, viewer.imageW, viewer.imageH), accent) }
                preview?.let { drawPreview(it, viewport, viewer.imageW, viewer.imageH) }
                drawPolygonDraft(state.draft.points, hover, viewport, viewer.imageW, viewer.imageH, accent)
            }
        }
        if (!state.draft.isEmpty) Chip(FREEFORM_HINT, colors.accent, Modifier.align(Alignment.TopStart).padding(Spacing.md))
        else state.oneShotTool?.let { Chip("Drawing: ${toolName(it)} - Esc to cancel", colors.accent, Modifier.align(Alignment.TopStart).padding(Spacing.md)) }
        menu?.let { open ->
            PopupMenu(canvasMenu(state, imageId, open.markIndex) { focus.requestFocus() }, { menu = null }, at = IntOffset(open.at.x.roundToInt(), open.at.y.roundToInt()))
        }
    }
}

private fun removeLastPoint(draft: PolygonDraft): Boolean = !draft.isEmpty && true.also { draft.removeLast() }

private fun planAt(hover: Offset, viewer: ReviewViewerState, state: ReviewState, marks: List<MarkDraft>): PressPlan? {
    val viewport = viewer.viewport ?: return null
    val (nx, ny) = normalised(viewport, viewer, hover)
    return ReviewPointerModel.press(state.effectiveTool, false, marks, state.selectedMark, nx, ny, viewer.imageW, viewer.imageH, HIT_TOLERANCE_PX / viewport.scale)
}

/**
 * Esc cancels the running gesture first and Delete is ignored while one runs; both, like the letter keys, return false when they have nothing to do so
 * outer handlers still see them. Ctrl, Alt and the other modifiers are never consumed.
 */
private fun handleKey(event: KeyEvent, state: ReviewState, imageId: Long, viewer: ReviewViewerState, active: ActiveGesture): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val chorded = event.isCtrlPressed || event.isMetaPressed || event.isShiftPressed
    return when (ReviewPointerModel.keyAction(event.key, chorded)) {
        KeyAction.ARM_RECTANGLE -> true.also { state.arm(ReviewTool.RECTANGLE) }
        KeyAction.ARM_ELLIPSE -> true.also { state.arm(ReviewTool.ELLIPSE) }
        KeyAction.ARM_PIN -> true.also { state.arm(ReviewTool.PIN) }
        KeyAction.ARM_FREEFORM -> true.also { state.arm(ReviewTool.FREEFORM) }
        KeyAction.DEFAULT_TOOL -> true.also { state.chooseTool(ReviewTool.POINTER) }
        KeyAction.CANCEL -> active.cancel() || state.cancel()
        KeyAction.DELETE -> active.running || removeLastPoint(state.draft) || state.selectedMark?.let { state.deleteMark(imageId, it) } != null
        KeyAction.CLOSE_POLYGON -> !state.draft.isEmpty && state.closeDraft(imageId, viewer.imageW, viewer.imageH)
        null -> false
    }
}
