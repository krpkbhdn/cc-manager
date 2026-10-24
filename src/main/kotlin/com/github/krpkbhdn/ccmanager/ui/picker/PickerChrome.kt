package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Kbd
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.components.isConfirmKey
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val headerBadgeSize = 40.dp
private val shortcutHints = listOf(listOf("↑", "↓") to "browse", listOf("Enter") to "open", listOf("Ctrl", "Enter") to "add", listOf("Esc") to "close")

@Composable
fun PickerHeader(modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.md, top = Spacing.lg, bottom = Spacing.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(headerBadgeSize).clip(RoundedCornerShape(Radius.md)).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            Glyph(Icons.FolderPlus, colors.accent, size = IconSize.xlarge)
        }
        Column(Modifier.weight(1f)) {
            Label("Add project", style = Type.subtitle)
            Label("Choose the folder Claude Code should work in", color = colors.textSecondary, style = Type.small)
        }
    }
}

/** Filter box for the current list; Down or Enter moves into the list, Esc clears the text. */
@Composable
fun FilterRow(state: FolderPickerState, count: Int, onFocusList: () -> Unit, modifier: Modifier = Modifier) {
    PickerField(
        state.filter, "Filter this folder", modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md),
        leading = { Glyph(Icons.Search, colors.textTertiary) },
        trailing = { Label(if (count == 1) "1 folder" else "$count folders", color = colors.textTertiary, style = Type.caption) },
    ) { event ->
        (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionDown || isConfirmKey(event.key))).also { if (it) onFocusList() }
    }
}

/** Inline "New folder" editor shown above the list; validates the name while typing. */
@Composable
fun NewFolderRow(state: FolderPickerState, modifier: Modifier = Modifier) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    LaunchedEffect(state.newFolderName.text) { state.validateNewFolder() }
    Column(modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            PickerField(
                state.newFolderName, "New folder name", Modifier.weight(1f), focus, invalid = state.newFolderError != null,
                leading = { Glyph(Icons.FolderPlus, colors.accent) },
            ) { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    isConfirmKey(event.key) -> true.also { state.createFolder() }
                    event.key == Key.Escape -> true.also { state.cancelCreatingFolder() }
                    else -> false
                }
            }
            PrimaryButton("Create", state::createFolder, enabled = state.newFolderName.text.isNotBlank() && state.newFolderError == null)
            SecondaryButton("Cancel", state::cancelCreatingFolder)
        }
        state.newFolderError?.let { Label(it, color = colors.danger, style = Type.small) }
    }
}

@Composable
private fun ShortcutHints() {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
        shortcutHints.forEach { (keys, action) ->
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                keys.forEach { Kbd(it) }
                Label(action, color = colors.textTertiary, style = Type.caption)
            }
        }
    }
}

@Composable
fun PickerFooter(state: FolderPickerState, check: PathCheck, onCancel: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(colors.sidebar)) {
        HorizontalRule()
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Glyph(Icons.FolderOpen, colors.accent)
                    Label(state.target.toString(), Modifier.weight(1f), style = Type.monoMedium, tooltipWhenTruncated = true)
                }
                if (check.message == null) ShortcutHints() else Label(check.message, color = colors.danger, style = Type.caption)
            }
            SecondaryButton("New folder", state::startCreatingFolder, icon = Icons.Plus, enabled = !state.creatingFolder && state.listing is Listing.Loaded)
            SecondaryButton("Cancel", onCancel)
            PrimaryButton("Add project", onConfirm, icon = Icons.Check, enabled = check == PathCheck.Valid)
        }
    }
}
