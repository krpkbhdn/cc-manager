package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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

val topBarHeight = 40.dp
private val searchWidth = 380.dp
private val badgeOffsetX = 9.dp
private val badgeOffsetY = (-7).dp
private const val SEARCH_FRACTION = 0.9f
private const val MAX_BADGE = 99

/** Slim in-app bar under the native title bar: centred search pill, theme toggle and notifications. */
@Composable
fun TopBar(state: AppState, modifier: Modifier = Modifier) {
    Column(modifier.background(colors.sidebar)) {
        Row(Modifier.fillMaxWidth().height(topBarHeight), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f))
            SearchPill({ state.paletteOpen = true })
            Row(Modifier.weight(1f).padding(end = Spacing.sm), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                ThemeToggle(state)
                NotificationBell(state)
            }
        }
        HorizontalRule()
    }
}

@Composable
private fun SearchPill(onClick: () -> Unit) {
    Pressable(onClick, Modifier.widthIn(max = searchWidth).fillMaxWidth(SEARCH_FRACTION).height(28.dp), shape = RoundedCornerShape(Radius.md), base = colors.hoverOverlay) {
        Row(Modifier.fillMaxWidth().fillMaxHeight().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(Icons.Search, colors.textTertiary, size = IconSize.medium)
            Label("Search or jump to", Modifier.weight(1f), color = colors.textTertiary, style = Type.small)
            Shortcut(AppShortcut.PALETTE)
        }
    }
}

@Composable
private fun ThemeToggle(state: AppState) {
    val dark = colors.dark
    IconButton(
        if (dark) Icons.Sun else Icons.Moon,
        { state.setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK) },
        tooltip = if (dark) "Switch to light theme" else "Switch to dark theme",
        hint = AppShortcut.TOGGLE_THEME.hint(),
    )
}

@Composable
private fun NotificationBell(state: AppState) {
    Box {
        IconButton(Icons.Bell, { state.notificationsOpen = !state.notificationsOpen }, tooltip = "Notifications", hint = AppShortcut.NOTIFICATIONS.hint(), active = state.notificationsOpen)
        if (state.unreadCount > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).offset(badgeOffsetX, badgeOffsetY).clip(RoundedCornerShape(Radius.pill)).background(colors.danger).padding(horizontal = Spacing.xs + 1.dp),
                contentAlignment = Alignment.Center,
            ) { Label(state.unreadCount.coerceAtMost(MAX_BADGE).toString(), color = Color.White, style = Type.numericCaption) }
        }
    }
}
