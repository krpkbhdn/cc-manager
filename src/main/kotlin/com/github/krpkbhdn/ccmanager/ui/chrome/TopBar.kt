package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Shortcut
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.theme.titleBarHeight

private val searchWidth = 380.dp
private val badgeOffsetX = 2.dp
private val badgeOffsetY = (-2).dp
private const val SEARCH_FRACTION = 0.9f
private const val MAX_BADGE = 99

/** Fallback bar under the native OS title bar (no JetBrainsRuntime): search pill centred while it fits, theme toggle, usage and notifications. */
@Composable
fun TopBar(state: AppState, modifier: Modifier = Modifier) {
    var actionsWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    Column(modifier.background(colors.sidebar)) {
        Row(Modifier.fillMaxWidth().height(titleBarHeight), verticalAlignment = Alignment.CenterVertically) {
            Spacer(Modifier.width(actionsWidth))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) { SearchPill({ state.paletteOpen = true }) }
            Row(
                Modifier.padding(end = Spacing.sm).onSizeChanged { actionsWidth = with(density) { (it.width.toDp() + Spacing.sm) } },
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemeToggle(state)
                UsageIndicator(state)
                NotificationBell(state)
            }
        }
        HorizontalRule()
    }
}

@Composable
internal fun SearchPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pressable(onClick, modifier.widthIn(max = searchWidth).fillMaxWidth(SEARCH_FRACTION).height(28.dp), shape = RoundedCornerShape(Radius.md), base = colors.hoverOverlay) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(Icons.Search, colors.textTertiary, size = IconSize.medium)
            Label("Search or jump to", Modifier.weight(1f), color = colors.textTertiary, style = Type.small)
            Shortcut(AppShortcut.PALETTE)
        }
    }
}

@Composable
internal fun ThemeToggle(state: AppState) {
    val dark = colors.dark
    IconButton(
        if (dark) Icons.Sun else Icons.Moon,
        { state.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
        tooltip = if (dark) "Switch to light theme" else "Switch to dark theme",
        hint = AppShortcut.TOGGLE_THEME.hint(),
    )
}

@Composable
internal fun NotificationBell(state: AppState) {
    Box {
        IconButton(Icons.Bell, { state.notificationsOpen = !state.notificationsOpen }, tooltip = "Notifications", hint = AppShortcut.NOTIFICATIONS.hint(), active = state.notificationsOpen)
        if (state.notifications.unreadCount > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(badgeOffsetX, badgeOffsetY).clip(RoundedCornerShape(Radius.pill)).background(colors.danger).padding(horizontal = Spacing.xs + 1.dp),
                contentAlignment = Alignment.Center,
            ) { Label(state.notifications.unreadCount.coerceAtMost(MAX_BADGE).toString(), color = Color.White, style = Type.numericCaption) }
        }
    }
}
