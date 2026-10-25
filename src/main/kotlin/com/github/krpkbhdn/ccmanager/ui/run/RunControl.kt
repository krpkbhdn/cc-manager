package com.github.krpkbhdn.ccmanager.ui.run

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val selectorHeight = 28.dp
private val selectorMaxLabel = 160.dp
private const val MISSING_HINT = "directory missing"

class RunControlActions(
    val onSelect: (Long) -> Unit,
    val onRun: (Long) -> Unit,
    val onStop: (Long) -> Unit,
    val onAdd: () -> Unit,
    val onEdit: (Long) -> Unit,
)

/** IntelliJ-style run toolbar: configuration selector, Run (Restart while running) and Stop; just an add button without configurations. */
@Composable
fun RunControl(entries: List<RunEntry>, selected: RunEntry?, actions: RunControlActions, modifier: Modifier = Modifier) {
    if (selected == null) {
        IconButton(Icons.SquareTerminal, actions.onAdd, modifier, tooltip = "Add run configuration")
        return
    }
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        ConfigurationSelector(entries, selected, actions)
        IconButton(
            if (selected.running) Icons.Refresh else Icons.Play,
            { actions.onRun(selected.id) },
            tooltip = if (selected.directoryMissing) "The run directory is missing" else if (selected.running) "Restart ${selected.name}" else "Run ${selected.name}",
            hint = AppShortcut.RUN.hint(),
            tint = colors.success,
            enabled = !selected.directoryMissing,
        )
        IconButton(Icons.Stop, { actions.onStop(selected.id) }, tooltip = "Stop ${selected.name}", tint = colors.danger, enabled = selected.running)
    }
}

@Composable
private fun ConfigurationSelector(entries: List<RunEntry>, selected: RunEntry, actions: RunControlActions) {
    var expanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.md)
    Box {
        Pressable({ expanded = true }, Modifier.height(selectorHeight).border(1.dp, colors.borderStrong, shape), shape, base = colors.surfaceRaised, hover = colors.surfaceHover) {
            Row(Modifier.padding(horizontal = Spacing.md).height(selectorHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                if (selected.directoryMissing) Glyph(Icons.Alert, colors.warning, size = IconSize.medium)
                Label(selected.name, Modifier.widthIn(max = selectorMaxLabel), style = Type.smallMedium, tooltipWhenTruncated = true)
                Glyph(Icons.ChevronDown, colors.textSecondary, size = IconSize.medium)
            }
        }
        if (expanded) PopupMenu(menuEntries(entries, selected, actions), onDismiss = { expanded = false })
    }
}

private fun menuEntries(entries: List<RunEntry>, selected: RunEntry, actions: RunControlActions): List<MenuEntry> =
    entries.map { entry ->
        MenuEntry(
            entry.name,
            icon = when {
                entry.directoryMissing -> Icons.Alert
                entry.id == selected.id -> Icons.Check
                else -> null
            },
            hint = if (entry.directoryMissing) MISSING_HINT else entry.directoryHint,
        ) { actions.onSelect(entry.id) }
    } + listOf(
        MenuEntry("Add configuration...", Icons.Plus, dividerAbove = true, onSelect = actions.onAdd),
        MenuEntry("Edit configurations...", Icons.Edit) { actions.onEdit(selected.id) },
    )
