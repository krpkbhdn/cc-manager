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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val headerBadgeSize = 36.dp
private const val SHORTCUT_HINT = "↑ ↓ browse   Enter open   Ctrl+Enter add   Backspace up   Ctrl+L path   Esc close"

@Composable
fun PickerHeader(onClose: () -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.md, top = Spacing.lg, bottom = Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.size(headerBadgeSize).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
            PickerGlyph(PickerIcon.FolderPlus, colors.accent, size = 20.dp)
        }
        Column(Modifier.weight(1f)) {
            Label("Add project", size = FontSize.heading, weight = FontWeight.SemiBold)
            Label("Choose the folder Claude Code should work in", color = colors.textSecondary, size = FontSize.small)
        }
        IconAction(AppIcon.Close, onClose)
    }
}

/** Filter box for the current list; Down moves into the list, Esc clears the text. */
@Composable
fun FilterRow(state: FolderPickerState, count: Int, onFocusList: () -> Unit, modifier: Modifier = Modifier) {
    PickerField(
        state.filter, "Filter this folder", modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(top = Spacing.md),
        leading = { PickerGlyph(PickerIcon.Search, colors.textTertiary) },
        trailing = { Label(if (count == 1) "1 folder" else "$count folders", color = colors.textTertiary, size = FontSize.caption) },
    ) { event ->
        (event.type == KeyEventType.KeyDown && (event.key == Key.DirectionDown || event.key == Key.Enter)).also { if (it) onFocusList() }
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
                leading = { PickerGlyph(PickerIcon.FolderPlus, colors.accent) },
            ) { event ->
                when {
                    event.type != KeyEventType.KeyDown -> false
                    event.key == Key.Enter -> true.also { state.createFolder() }
                    event.key == Key.Escape -> true.also { state.cancelCreatingFolder() }
                    else -> false
                }
            }
            PrimaryButton("Create", state::createFolder, enabled = state.newFolderName.text.isNotBlank() && state.newFolderError == null)
            SecondaryButton("Cancel", state::cancelCreatingFolder)
        }
        state.newFolderError?.let { Label(it, color = colors.danger, size = FontSize.small) }
    }
}

@Composable
fun PickerFooter(state: FolderPickerState, check: PathCheck, onCancel: () -> Unit, onConfirm: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().background(colors.sidebar)) {
        HorizontalRule()
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Glyph(AppIcon.Folder, colors.accent)
                    Label(state.target.toString(), Modifier.weight(1f), mono = true, size = FontSize.small, weight = FontWeight.Medium)
                }
                Label(check.message ?: SHORTCUT_HINT, color = if (check == PathCheck.Valid) colors.textTertiary else colors.danger, size = FontSize.caption)
            }
            SecondaryButton("New folder", state::startCreatingFolder, icon = AppIcon.Plus, enabled = !state.creatingFolder && state.listing is Listing.Loaded)
            SecondaryButton("Cancel", onCancel)
            PrimaryButton("Add project", onConfirm, icon = AppIcon.Check, enabled = check == PathCheck.Valid)
        }
    }
}
