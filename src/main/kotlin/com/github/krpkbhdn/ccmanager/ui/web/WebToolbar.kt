package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isShiftPressed
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val fieldHeight = 30.dp
private val addressMinWidth = 120.dp
private val addressMaxWidth = 320.dp
private val dividerHeight = 18.dp

/** Thin divider between logical groups of the toolbar. */
@Composable
private fun GroupDivider() = VerticalRule(Modifier.padding(horizontal = Spacing.xs).height(dividerHeight))

/** Groups separated by dividers: navigation and address | device and zoom | pick and lock | overlays | screenshot | console. Each switch is pushed into the page through [WebState]. */
@Composable
fun WebToolbar(view: WebView, state: WebState, modifier: Modifier = Modifier) {
    val page by view.host.page.collectAsState()
    val toggles = view.toggles
    val window = LocalWindowInfo.current
    Row(
        modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        IconButton(Icons.ChevronLeft, view.host::back, tooltip = "Back", enabled = page.canGoBack)
        IconButton(Icons.ChevronRight, view.host::forward, tooltip = "Forward", enabled = page.canGoForward)
        if (page.loading) IconButton(Icons.Stop, view.host::stop, tooltip = "Stop") else IconButton(Icons.Refresh, view.host::reload, tooltip = "Reload")
        AppTextField(
            view.address, Modifier.widthIn(min = addressMinWidth, max = addressMaxWidth).weight(1f, fill = false).padding(horizontal = Spacing.xs),
            placeholder = "http://localhost:3000", icon = Icons.Globe, mono = true,
            onSubmit = { state.go(view, view.address.text.toString()) }, height = fieldHeight,
        )
        GroupDivider()
        WebDeviceControls(view.device) { view.device = it }
        GroupDivider()
        IconButton(
            Icons.MousePointer, { state.perform(view, if (window.keyboardModifiers.isShiftPressed) WebAction.StickyPick else WebAction.TogglePick) },
            tooltip = "Pick element (I) - Shift+click or Shift+I keeps picking", active = toggles.pick,
        )
        IconButton(
            Icons.Hand, { state.perform(view, WebAction.ToggleLock) },
            tooltip = "Lock page (L) - the page ignores the mouse, the inspector still works", active = !view.interaction,
        )
        if (!view.interaction) Label("Locked", color = colors.accent, style = Type.caption)
        GroupDivider()
        WebOverlayMenu(toggles) { state.setToggles(view, it) }
        GroupDivider()
        WebScreenshotMenu(view, state, enabled = state.canShoot(view))
        WebReviewTargetMenu(view.projectId, state)
        if (view.shooting) Label("Capturing...", color = colors.accent, style = Type.caption)
        GroupDivider()
        IconButton(Icons.Terminal, { view.consoleOpen = !view.consoleOpen }, tooltip = "Console", active = view.consoleOpen)
    }
}
