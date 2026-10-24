package com.github.krpkbhdn.ccmanager.ui.chrome

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.window.DecoratedWindowScope
import org.jetbrains.jewel.window.TitleBar

private val markSize = 18.dp

/** The window's own title bar (JetBrainsRuntime decorations): app mark and name, command palette pill, theme toggle and notifications. */
@Composable
fun DecoratedWindowScope.AppTitleBar(state: AppState) {
    TitleBar {
        Row(Modifier.align(Alignment.Start).padding(start = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            icon?.let { Image(it, null, Modifier.size(markSize)) }
            Label(title, color = colors.textPrimary, style = Type.smallMedium)
        }
        SearchPill({ state.paletteOpen = true }, Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.align(Alignment.End), verticalAlignment = Alignment.CenterVertically) {
            ThemeToggle(state)
            NotificationBell(state)
        }
    }
}
