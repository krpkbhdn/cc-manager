package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val rowHeight = 44.dp
private val nameWidth = 104.dp
private val toolsWidth = 60.dp
private val actionSize = 28.dp

class RoleRowActions(
    val onEdit: () -> Unit,
    val onDuplicate: () -> Unit,
    val onDelete: () -> Unit,
    val onMoveUp: () -> Unit,
    val onMoveDown: () -> Unit,
)

/** One dense line per agent; a click or Enter opens the editor, hovering reveals reorder, duplicate and delete. */
@Composable
fun RoleRow(role: TeamRole, slot: Int, actions: RoleRowActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.md)
    Pressable(actions.onEdit, modifier.fillMaxWidth(), shape, colors.surface, colors.surfaceHover) { hovered ->
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md).padding(vertical = Spacing.xs + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            RoleAvatar(role.name(), slot, 28.dp)
            Row(Modifier.width(nameWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                if (role.lead()) Glyph(Icons.Crown, colors.accent, size = 13.dp)
                Label(role.name(), Modifier.weight(1f, fill = false), style = Type.bodyMedium, tooltipWhenTruncated = true)
            }
            Chip(roleModelChip(role), roleColor(slot))
            role.selection().effort()?.let { Chip(it, colors.textSecondary) }
            Label(ToolSelection.parse(role.draft.tools).summary, Modifier.width(toolsWidth), color = colors.textTertiary, style = Type.caption)
            Label(role.draft.description, Modifier.weight(1f), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
        }
        if (hovered) RowActions(actions, Modifier.align(Alignment.CenterEnd))
    }
}

@Composable
private fun RowActions(actions: RoleRowActions, modifier: Modifier) {
    Row(modifier.background(colors.surfaceHover, RoundedCornerShape(Radius.md)).padding(horizontal = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(Icons.ArrowUp, actions.onMoveUp, tooltip = "Move up", size = actionSize)
        IconButton(Icons.ArrowDown, actions.onMoveDown, tooltip = "Move down", size = actionSize)
        IconButton(Icons.Edit, actions.onEdit, tooltip = "Edit", size = actionSize)
        IconButton(Icons.Copy, actions.onDuplicate, tooltip = "Duplicate", size = actionSize)
        IconButton(Icons.Trash, actions.onDelete, tooltip = "Remove", tint = colors.danger, size = actionSize)
    }
}

@Composable
fun RosterEmptyHint(onAdd: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label("Only the lead so far. Add agents it can hand work to, such as a developer or a tester.", Modifier.weight(1f), color = colors.textSecondary, maxLines = 2)
        AppButton("New agent", onAdd, icon = Icons.Plus)
    }
}
