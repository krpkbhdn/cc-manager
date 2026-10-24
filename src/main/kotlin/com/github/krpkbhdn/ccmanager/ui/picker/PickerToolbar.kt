package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val toolButtonSize = 34.dp

/** Back, forward and up buttons, the path bar and the hidden-folders toggle. */
@Composable
fun PickerToolbar(state: FolderPickerState, onFocusList: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        IconButton(PickerIcons.ArrowLeft, { state.back(); onFocusList() }, tooltip = "Back", hint = "Alt+Left", enabled = state.history.canGoBack, size = toolButtonSize)
        IconButton(Icons.ArrowRight, { state.forward(); onFocusList() }, tooltip = "Forward", hint = "Alt+Right", enabled = state.history.canGoForward, size = toolButtonSize)
        IconButton(Icons.ArrowUp, { state.up(); onFocusList() }, tooltip = "Parent folder", hint = "Backspace", enabled = state.history.canGoUp, size = toolButtonSize)
        PathBar(state, onFocusList, Modifier.weight(1f).padding(horizontal = Spacing.sm))
        IconButton(
            Icons.Eye, { state.showHidden = !state.showHidden; onFocusList() },
            tooltip = if (state.showHidden) "Hide hidden folders" else "Show hidden folders", active = state.showHidden, size = toolButtonSize,
        )
    }
}
