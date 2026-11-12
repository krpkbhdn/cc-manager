package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.review.ReviewToolButtons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/**
 * Layer over the canvas that paints the drawn marks and hosts the drawing tools, the context menu and the popovers.
 * [onZoom] and [onPan] are the screen's, which owns zoom and pan; the mark layer calls them from drawing mode, where it takes the wheel.
 */
@Composable
fun WebPageOverlay(
    view: WebView,
    state: WebState,
    viewport: PageViewport,
    modifier: Modifier = Modifier,
    onZoom: (cursorDp: Offset, notches: Int) -> Unit = { _, _ -> },
    onPan: (dxDp: Double, dyDp: Double) -> Unit = { _, _ -> },
) {
    SideEffect { view.layout = viewport }
    val density = rememberUpdatedState(LocalDensity.current.density)
    val zoom = rememberUpdatedState(onZoom)
    val pan = rememberUpdatedState(onPan)
    val space = remember(view) { WebMarkSpace({ view.layout }, { view.scroll }, { density.value }, { dx, dy -> pan.value(dx, dy) }, { at, notches -> zoom.value(at, notches) }) }
    SideEffect { view.markSpace = space }
    CompositionLocalProvider(LocalWebState provides state) {
        Box(modifier.fillMaxSize()) {
            WebMarkLayer(view, state, space, { dx, dy -> pan.value(dx, dy) })
            if (view.drawMode) DrawingTools(view, state, Modifier.align(Alignment.TopCenter))
            WebContextMenu(view, state)
            WebFeedbackPopover(view, state)
            WebItemPopover(view, viewport)
        }
    }
}

/** The review tool buttons; the card swallows presses so a miss between buttons does not start a mark underneath. */
@Composable
private fun DrawingTools(view: WebView, state: WebState, modifier: Modifier) {
    Row(
        modifier.padding(top = Spacing.sm).card(RoundedCornerShape(Radius.md), colors.surfaceRaised).padding(Spacing.xs).pointerInput(Unit) { detectTapGestures { } },
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        ReviewToolButtons(view.drawTool) { state.perform(view, WebAction.SelectTool(it)) }
    }
}
