package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val fieldHeight = 30.dp
private val addressMaxWidth = 320.dp

/** Navigation, address field and the inspector switches; each switch is pushed into the page through [WebState]. */
@Composable
fun WebToolbar(view: WebView, state: WebState, modifier: Modifier = Modifier) {
    val page by view.host.page.collectAsState()
    val toggles = view.toggles
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(Icons.ChevronLeft, view.host::back, tooltip = "Back", enabled = page.canGoBack)
        IconButton(Icons.ChevronRight, view.host::forward, tooltip = "Forward", enabled = page.canGoForward)
        if (page.loading) IconButton(Icons.Stop, view.host::stop, tooltip = "Stop") else IconButton(Icons.Refresh, view.host::reload, tooltip = "Reload")
        AppTextField(
            view.address, Modifier.widthIn(max = addressMaxWidth).weight(1f, fill = false).padding(horizontal = Spacing.xs),
            placeholder = "http://localhost:3000", icon = Icons.Globe, mono = true,
            onSubmit = { state.go(view, view.address.text.toString()) }, height = fieldHeight,
        )
        IconButton(Icons.MousePointer, { state.setToggles(view, toggles.togglePick()) }, tooltip = "Pick element", active = toggles.pick)
        IconButton(Icons.Copy, { state.setToggles(view, toggles.copy(similar = !toggles.similar)) }, tooltip = "Highlight similar elements", active = toggles.similar)
        WebOverlayMenu(toggles) { state.setToggles(view, it) }
        WebDeviceControls(view.device) { view.device = it }
        IconButton(Icons.Terminal, { view.consoleOpen = !view.consoleOpen }, tooltip = "Console", active = view.consoleOpen)
    }
}
