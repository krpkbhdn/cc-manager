package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val toolButtonSize = 34.dp

@Composable
private fun ToolButton(icon: PickerIcon, tooltip: String, onClick: () -> Unit, enabled: Boolean = true, active: Boolean = false) {
    WithTooltip(tooltip) {
        Pressable(
            onClick, Modifier.size(toolButtonSize).alpha(if (enabled) 1f else Alpha.DISABLED), enabled = enabled,
            base = if (active) colors.surfaceSelected else Color.Transparent,
        ) {
            PickerGlyph(icon, if (active) colors.accent else colors.textSecondary, Modifier.align(Alignment.Center), size = 18.dp)
        }
    }
}

/** Back, forward and up buttons, the path bar and the hidden-folders toggle. */
@Composable
fun PickerToolbar(state: FolderPickerState, onFocusList: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        ToolButton(PickerIcon.ArrowLeft, "Back (Alt+Left)", { state.back(); onFocusList() }, state.history.canGoBack)
        ToolButton(PickerIcon.ArrowRight, "Forward (Alt+Right)", { state.forward(); onFocusList() }, state.history.canGoForward)
        ToolButton(PickerIcon.ArrowUp, "Parent folder (Backspace)", { state.up(); onFocusList() }, state.history.canGoUp)
        PathBar(state, onFocusList, Modifier.weight(1f).padding(horizontal = Spacing.sm))
        ToolButton(PickerIcon.Eye, if (state.showHidden) "Hide hidden folders" else "Show hidden folders", { state.showHidden = !state.showHidden; onFocusList() }, active = state.showHidden)
    }
}
