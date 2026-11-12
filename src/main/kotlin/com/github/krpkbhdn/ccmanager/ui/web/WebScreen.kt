package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.review.SessionTargetDialog

private val sidePanelWidth = 340.dp

/** Web review tab: the project's page in an in-app browser with its inspector and review items beside it and the page console below. */
@Composable
fun WebScreen(projectId: Long, state: WebState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { state.prepare() }
    val unsupported = state.unsupportedReason()
    if (unsupported != null) {
        EmptyState(Icons.Globe, "Web review is not available", unsupported, modifier)
        return
    }
    state.browserFailure?.let { failure ->
        EmptyState(Icons.Globe, "The browser could not start", failure, modifier) { AppButton("Try again", state::retry, icon = Icons.Refresh) }
        return
    }
    if (!state.browserReady) {
        EmptyState(Icons.Globe, "Starting the browser...", "This takes a few seconds the first time.", modifier)
        return
    }
    val view = state.liveView(projectId)
    LaunchedEffect(projectId, view == null, state.browserFailure) { if (view == null) state.open(projectId) }
    DisposableEffect(projectId) {
        state.shown(projectId)
        onDispose { state.hidden(projectId) }
    }
    if (view == null) {
        EmptyState(Icons.Globe, "Opening the page...", "", modifier)
        return
    }
    Column(modifier.fillMaxSize().onFocusChanged { view.screenFocused = it.hasFocus }) {
        WebToolbar(view, state)
        HorizontalRule()
        Row(Modifier.weight(1f)) {
            BoxWithConstraints(Modifier.weight(1f).clipToBounds()) {
                val pans = remember(projectId) { mutableStateMapOf<DeviceSelection, Offset>() }
                val pan = pans[view.device.copy(zoom = DeviceZoom.Fit)] ?: Offset.Zero
                val viewport = PageViewport.of(view.device, maxWidth.value.toDouble(), maxHeight.value.toDouble(), pan.x.toDouble(), pan.y.toDouble())
                val zoomAt = { cursor: Offset, notches: Int ->
                        // Responsive has no zoom: the first zoom turns the canvas size into a custom device.
                        val availW = maxWidth.value.toDouble()
                        val availH = maxHeight.value.toDouble()
                        val device = view.device
                        val base = if (device.responsive) DeviceSelection.custom(viewport.widthCss, viewport.heightCss, device, fromResponsive = true) else device
                        val current = pans[base.copy(zoom = DeviceZoom.Fit)] ?: Offset.Zero
                        val from = PageViewport.of(base.copy(zoom = if (device.responsive) DeviceZoom.Fit else device.zoom), availW, availH, current.x.toDouble(), current.y.toDouble())
                        val change = from.zoomAt(cursor, notches)
                        val zoomed = base.copy(zoom = DeviceZoom.Percent(change.percent))
                        pans.clear()
                        pans[zoomed.copy(zoom = DeviceZoom.Fit)] = PageViewport.panAfterZoom(zoomed, change, availW, availH)
                        view.device = zoomed
                }
                val panBy = { dx: Double, dy: Double ->
                    val key = view.device.copy(zoom = DeviceZoom.Fit)
                    val next = viewport.panned(pans[key] ?: Offset.Zero, dx, dy)
                    pans.clear()
                    pans[key] = next
                }
                WebCanvas(
                    view.host, viewport, Modifier.fillMaxSize(), device = view.device,
                    onZoom = zoomAt,
                    interaction = view.interaction,
                    blocked = view.shooting,
                    hotKeyModes = { view.hotKeyModes() },
                    pageEditing = { view.pageEditing },
                    onAction = { state.perform(view, it) { notches -> zoomAt(Offset(maxWidth.value / 2, maxHeight.value / 2), notches) } },
                    onPointer = { page, canvas -> view.lastPointer = PointerSpot(page, canvas) },
                    onPan = panBy,
                    onContextMenu = { page, canvas -> state.openContextMenu(view, page, canvas) },
                    canGrabFocus = view::canGrabCanvasFocus,
                    focusSignal = view.contextMenu == null && view.itemPopover == null && !view.regionSelecting,
                )
                WebPageOverlay(view, state, viewport)
                if (view.regionSelecting) {
                    WebRegionSelect(viewport, onSelected = { state.regionSelected(view, it) }, onCancel = { state.perform(view, WebAction.CancelRegion) })
                }
            }
            VerticalRule()
            WebSidePanel(projectId, view, state, Modifier.width(sidePanelWidth))
        }
        if (view.consoleOpen) {
            HorizontalRule()
            WebConsolePanel(view)
        }
    }
    state.sendTarget(projectId)?.let { SessionTargetDialog(it.candidates, { id -> state.sendTo(it.revision, id) }, { state.dismissSendTarget(projectId) }) }
}
