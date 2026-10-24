package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.ContextMenuArea
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.git.KindBadge
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeGroup
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeItem
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeRef
import com.github.krpkbhdn.ccmanager.ui.git.model.kindIn
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val rowHeight = 28.dp
private val indentStep = 14.dp

internal class GroupAction(val label: String, val run: () -> Unit)

@Composable
internal fun GroupHeader(item: ChangeItem.Header, onToggle: () -> Unit, action: GroupAction?) {
    val tint = if (item.group == ChangeGroup.CONFLICTS) colors.danger else colors.textSecondary
    Pressable(onToggle, Modifier.fillMaxWidth().padding(top = Spacing.xs), RoundedCornerShape(Radius.sm)) {
        Row(Modifier.height(rowHeight).padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(if (item.collapsed) Icons.ChevronRight else Icons.ChevronDown, colors.textTertiary, size = IconSize.medium)
            Label(item.group.title.uppercase(), color = tint, style = Type.overline)
            CountBadge(item.count, tint = tint)
            Box(Modifier.weight(1f))
            if (action != null) GhostButton(action.label, action.run)
        }
    }
}

@Composable
internal fun FolderRow(item: ChangeItem.Folder, onToggle: () -> Unit, onStage: ((Boolean) -> Unit)?) {
    val staged = item.group == ChangeGroup.STAGED
    Pressable(onToggle, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm)) {
        Row(Modifier.height(rowHeight).padding(start = Spacing.sm + indentStep * item.row.depth, end = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(if (item.row.collapsed) Icons.ChevronRight else Icons.ChevronDown, colors.textTertiary, size = IconSize.small)
            if (onStage != null && item.group != ChangeGroup.CONFLICTS) AppCheckbox(staged, { onStage(!staged) })
            Glyph(Icons.Folder, colors.textTertiary, size = IconSize.medium)
            Label(item.row.label, Modifier.weight(1f), color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
            Label(item.row.fileCount.toString(), color = colors.textTertiary, style = Type.numericCaption)
        }
    }
}

@Composable
internal fun FileRow(item: ChangeItem.File, selected: ChangeRef?, actions: ChangeListActions) {
    val change = item.row.change
    val staged = item.group == ChangeGroup.STAGED
    val ref = ChangeRef(change.path(), staged)
    val isSelected = selected == ref
    val conflict = item.group == ChangeGroup.CONFLICTS
    ContextMenuArea({ fileMenu(item, actions) }) {
        Pressable({ actions.onSelect(ref) }, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm), base = if (isSelected) colors.accentSoft else Color.Transparent) { hovered ->
            Row(
                Modifier.height(rowHeight).padding(start = Spacing.sm + indentStep * item.row.depth, end = Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (conflict) Box(Modifier.size(AppCheckboxSlot)) else AppCheckbox(staged, { if (staged) actions.onUnstage(listOf(change.path())) else actions.onStage(listOf(change.path())) })
                KindBadge(if (conflict) GitStatus.Kind.UNMERGED else change.kindIn(staged))
                FileLabel(item.row.name, item.row.directory, isSelected, Modifier.weight(1f))
                if (hovered) HoverActions(item, actions)
            }
        }
    }
}

private val AppCheckboxSlot = 22.dp

@Composable
private fun FileLabel(name: String, directory: String, selected: Boolean, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(name, color = if (selected) colors.textPrimary else colors.textSecondary, style = Type.smallMedium, tooltipWhenTruncated = true)
        if (directory.isNotEmpty()) Label(directory, Modifier.weight(1f), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
    }
}

@Composable
private fun HoverActions(item: ChangeItem.File, actions: ChangeListActions) {
    val path = item.row.change.path()
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        if (item.group == ChangeGroup.UNSTAGED) IconButton(Icons.Undo, { actions.onDiscard(listOf(path)) }, tooltip = "Discard changes", size = 22.dp, tint = colors.textTertiary)
        IconButton(Icons.Code, { actions.onOpenIde(path) }, tooltip = "Open in IDE", size = 22.dp, tint = colors.textTertiary)
    }
}

private fun fileMenu(item: ChangeItem.File, actions: ChangeListActions): List<MenuEntry> {
    val path = item.row.change.path()
    val common = listOf(
        MenuEntry("Show diff", Icons.Diff, dividerAbove = true) { actions.onSelect(ChangeRef(path, item.group == ChangeGroup.STAGED)) },
        MenuEntry("Show history", Icons.Log) { actions.onShowHistory(path) },
        MenuEntry("Copy path", Icons.Copy) { copyToClipboard(path) },
        MenuEntry("Open folder", Icons.FolderOpen) { actions.onOpenFolder(path) },
        MenuEntry("Open in IDE", Icons.Code) { actions.onOpenIde(path) },
    )
    return when (item.group) {
        ChangeGroup.CONFLICTS -> listOf(
            MenuEntry("Accept ours", Icons.Check) { actions.onResolve(path, ConflictSide.OURS) },
            MenuEntry("Accept theirs", Icons.Check) { actions.onResolve(path, ConflictSide.THEIRS) },
            MenuEntry("Mark resolved", Icons.CircleCheck) { actions.onMarkResolved(path) },
        ) + common
        ChangeGroup.STAGED -> listOf(MenuEntry("Unstage", Icons.Minus) { actions.onUnstage(listOf(path)) }) + common
        ChangeGroup.UNSTAGED -> listOf(
            MenuEntry("Stage", Icons.Plus) { actions.onStage(listOf(path)) },
            MenuEntry("Discard changes...", Icons.Undo, danger = true) { actions.onDiscard(listOf(path)) },
        ) + common
    }
}
