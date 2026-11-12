package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier

/** Layer over the canvas that hosts the context menu and the popovers. */
@Composable
fun WebPageOverlay(view: WebView, state: WebState, viewport: PageViewport, modifier: Modifier = Modifier) {
    SideEffect { view.layout = viewport }
    CompositionLocalProvider(LocalWebState provides state) {
        Box(modifier.fillMaxSize()) {
            WebContextMenu(view, state)
            WebFeedbackPopover(view, state)
            WebItemPopover(view, viewport)
        }
    }
}
