package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.nativeKeyCode
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.rememberTextMeasurer
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.ui.review.ActiveGesture
import com.github.krpkbhdn.ccmanager.ui.review.CanvasActions
import com.github.krpkbhdn.ccmanager.ui.review.HIT_TOLERANCE_PX
import com.github.krpkbhdn.ccmanager.ui.review.PressPlan
import com.github.krpkbhdn.ccmanager.ui.review.ReviewPointerModel
import com.github.krpkbhdn.ccmanager.ui.review.Viewport
import com.github.krpkbhdn.ccmanager.ui.review.cursorFor
import com.github.krpkbhdn.ccmanager.ui.review.drawHandles
import com.github.krpkbhdn.ccmanager.ui.review.drawMark
import com.github.krpkbhdn.ccmanager.ui.review.drawPolygonDraft
import com.github.krpkbhdn.ccmanager.ui.review.drawPreview
import com.github.krpkbhdn.ccmanager.ui.review.hoverTracking
import com.github.krpkbhdn.ccmanager.ui.review.markGestures
import com.github.krpkbhdn.ccmanager.ui.review.normalised
import com.github.krpkbhdn.ccmanager.ui.review.pointerIconOf
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.web.input.WebInputMapper
import com.github.krpkbhdn.ccmanager.ui.web.input.keyModifierMask
import com.github.krpkbhdn.ccmanager.web.WebItemStatus

private const val PAGE_NOTCH_DP = WebInputMapper.PIXELS_PER_NOTCH.toDouble()

/**
 * Paints the page's drawn marks over the canvas (the items of the current device scope, numbered like the list). In drawing mode it also
 * takes the pointer: the review tools draw, move and resize marks, the wheel scrolls the page (Ctrl zooms, Shift pans) and plain keys keep
 * their drawing-mode meaning; outside drawing mode it takes no input at all.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal fun WebMarkLayer(view: WebView, state: WebState, space: WebMarkSpace, onPan: (Double, Double) -> Unit, modifier: Modifier = Modifier) {
    val doc = view.scroll
    val measurer = rememberTextMeasurer()
    val accent = colors.accent
    val focus = remember { FocusRequester() }
    val active = remember(space) { ActiveGesture() }
    var preview by remember { mutableStateOf<MarkDraft?>(null) }
    var hover by remember { mutableStateOf<Offset?>(null) }
    var panning by remember { mutableStateOf(false) }
    val actions = remember(space, view, state) {
        CanvasActions(
            tool = { view.drawTool }, marks = { markDrafts(view) }, selected = { selectedIndex(view) },
            onPreview = { preview = it }, onAddMark = { state.addMark(view, it) },
            onSelect = { view.selectedItemId = it?.let { index -> view.visibleMarks.getOrNull(index)?.id() } },
            onEdit = { index, edited -> view.visibleMarks.getOrNull(index)?.let { state.editMark(view, it, edited) } },
            onContextMenu = {}, onDiscardDraw = {}, onPanning = { panning = it }, draft = view.polygon,
            onCloseDraft = { state.closePolygon(view) }, onCancelDraft = view.polygon::clear, active = active,
        )
    }
    LaunchedEffect(view.pageItems, doc) {
        doc?.let { settledMarks(view.pendingMarks, view.pageItems, it).forEach(view.pendingMarks::remove) }
    }
    LaunchedEffect(view.drawMode) { if (!view.drawMode) view.polygon.clear() }
    val plan = hover?.takeIf { view.drawMode }?.let { planAt(it, space, view) }
    val density = LocalDensity.current.density
    val input = if (!view.drawMode) Modifier else Modifier
        .pageBounded(view, density)
        .focusRequester(focus).focusable()
        .onPreviewKeyEvent { handleKey(it, view, state, active) }
        .onPointerEvent(PointerEventType.Press) { focus.requestFocus() }
        .onPointerEvent(PointerEventType.Scroll) { event ->
            val change = event.changes.first()
            if (pageBox(view, density)?.contains(change.position) == false) return@onPointerEvent
            val modifiers = event.keyboardModifiers
            when {
                modifiers.isCtrlPressed -> space.zoomAt(change.position.x, change.position.y, Viewport.zoomFactor(change.scrollDelta.y))
                modifiers.isShiftPressed -> onPan(change.scrollDelta.y * PAGE_NOTCH_DP, change.scrollDelta.x * PAGE_NOTCH_DP)
                else -> view.host.runScript(InspectorBridge.scrollBy(change.scrollDelta.x * PAGE_NOTCH_DP, change.scrollDelta.y * PAGE_NOTCH_DP))
            }
            change.consume()
        }
        .hoverTracking { hover = it }
        .markGestures(space, actions)
        .pointerHoverIcon(pointerIconOf(cursorFor(plan, selectedIndex(view), panning)))
    Box(modifier.fillMaxSize().then(input)) {
        val viewport = space.viewport
        if (viewport != null && doc != null) {
            val items = view.visibleMarks
            val drafts = markDrafts(view)
            val selected = selectedIndex(view)
            val editable = selected?.takeIf { items[it].status() == WebItemStatus.OPEN }
            val box = pageBox(view, density)
            Canvas(Modifier.fillMaxSize()) {
                clipRect(box?.left ?: 0f, box?.top ?: 0f, box?.right ?: size.width, box?.bottom ?: size.height) {
                    drafts.forEachIndexed { index, mark -> drawMark(mark, items[index].n() - 1, index == selected, viewport, space.imageW, space.imageH, measurer) }
                    if (view.drawMode) editable?.let { drafts.getOrNull(it) }?.let { drawHandles(it, viewport, space.imageW, space.imageH, accent) }
                    view.draftShape?.let { drawPreview(it.toMark(doc.w, doc.h), viewport, space.imageW, space.imageH) }
                    preview?.let { drawPreview(it, viewport, space.imageW, space.imageH) }
                    drawPolygonDraft(view.polygon.points, hover, viewport, space.imageW, space.imageH, accent)
                }
            }
        }
    }
}

/** The device frame in canvas px; marks paint and take the pointer only inside it. */
private class PageBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    fun contains(at: Offset) = at.x >= left && at.x < right && at.y >= top && at.y < bottom
}

private fun pageBox(view: WebView, density: Float): PageBox? = view.layout?.let {
    val left = (it.offsetX * density).toFloat()
    val top = (it.offsetY * density).toFloat()
    PageBox(left, top, left + (it.widthCss * it.scale * density).toFloat(), top + (it.heightCss * it.scale * density).toFloat())
}

/**
 * First in the pointer chain: a press outside the frame is used up before the gestures see it, and every move inside the frame is
 * remembered as the last pointer position (the canvas below gets no moves while this layer is on top).
 */
private fun Modifier.pageBounded(view: WebView, density: Float): Modifier = pointerInput(view, density) {
    awaitPointerEventScope {
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val change = event.changes.first()
            val box = pageBox(view, density) ?: continue
            val inside = box.contains(change.position)
            if (!inside && event.type == PointerEventType.Press) event.changes.forEach { it.consume() }
            if (inside && event.type == PointerEventType.Move) {
                val page = view.layout!!.toPage(Offset(change.position.x / density, change.position.y / density))
                view.lastPointer = PointerSpot(page, change.position)
            }
        }
    }
}

/** The visible marks as review marks; geometry the user changed that is not saved yet wins over the stored one. */
private fun markDrafts(view: WebView): List<MarkDraft> {
    val doc = view.scroll?.takeIf { it.w > 0 && it.h > 0 } ?: return emptyList()
    return view.visibleMarks.map { view.pendingMarks[it.id()] ?: it.shape().toMark(doc.w, doc.h, it.comment()) }
}

private fun selectedIndex(view: WebView): Int? = view.visibleMarks.indexOfFirst { it.id() == view.selectedItemId }.takeIf { it >= 0 }

private fun planAt(hover: Offset, space: WebMarkSpace, view: WebView): PressPlan? {
    val viewport = space.viewport ?: return null
    val (nx, ny) = normalised(viewport, space, hover)
    return ReviewPointerModel.press(view.drawTool, false, markDrafts(view), selectedIndex(view), nx, ny, space.imageW, space.imageH, HIT_TOLERANCE_PX / viewport.scale)
}

/** Esc and Backspace first undo what is half drawn; every other plain key means what [WebHotKeys] says for drawing mode. Modified keys are never consumed. */
private fun handleKey(event: KeyEvent, view: WebView, state: WebState, active: ActiveGesture): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    if (event.key == Key.Escape && (active.cancel() || cancelPolygon(view))) return true
    if ((event.key == Key.Backspace || event.key == Key.Delete) && removeLastPoint(view)) return true
    val modifiers = keyModifierMask(event.isCtrlPressed, event.isShiftPressed, event.isAltPressed, event.isMetaPressed)
    val action = WebHotKeys.resolve(event.key.nativeKeyCode, modifiers, view.hotKeyModes(), view.pageEditing) ?: return false
    state.perform(view, action)
    return true
}

private fun cancelPolygon(view: WebView): Boolean = !view.polygon.isEmpty && true.also { view.polygon.clear() }

private fun removeLastPoint(view: WebView): Boolean = !view.polygon.isEmpty && true.also { view.polygon.removeLast() }
