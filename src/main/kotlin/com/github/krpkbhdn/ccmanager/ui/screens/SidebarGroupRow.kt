package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.ContextMenuArea
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.GroupDialogRequest
import com.github.krpkbhdn.ccmanager.ui.state.RenameRequest
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val groupRowHeight = 38.dp

class GroupActions(
    val onOpen: () -> Unit,
    val onStartTask: () -> Unit,
    val onEdit: () -> Unit,
    val onRename: () -> Unit,
    val onRemove: () -> Unit,
)

fun AppState.groupActions(group: Project) = GroupActions(
    onOpen = { navigate(Route.Project(group.id)) },
    onStartTask = { openTaskDialog(group.id) },
    onEdit = { projects.groupDialog = GroupDialogRequest(group) },
    onRename = { projects.renaming = RenameRequest(group) },
    onRemove = { projects.requestRemove(group) },
)

private fun groupMenu(actions: GroupActions): List<MenuEntry> = listOf(
    MenuEntry("Start task with team", Icons.Users) { actions.onStartTask() },
    MenuEntry("Edit group", Icons.FolderTree) { actions.onEdit() },
    MenuEntry("Rename", Icons.Edit) { actions.onRename() },
    MenuEntry("Remove group", Icons.Trash, danger = true) { actions.onRemove() },
)

/** Sidebar entry of a project group: chevron to fold its members, folder, name and the worst status of the group. */
@Composable
fun GroupRow(
    group: Project,
    status: AgentStatus,
    memberCount: Int,
    expanded: Boolean,
    selected: Boolean,
    collapsed: Boolean,
    actions: GroupActions,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    ContextMenuArea({ groupMenu(actions) }, modifier) {
        Box {
            WithTooltip(if (collapsed) group.name else null) {
                Pressable(actions.onOpen, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (selected) colors.accentSoft else Color.Transparent) { hovered ->
                    Row(
                        Modifier.height(groupRowHeight).fillMaxWidth().padding(start = Spacing.xs, end = Spacing.sm),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(Spacing.sm),
                    ) {
                        if (collapsed) {
                            Glyph(Icons.Folder, colors.textSecondary, size = IconSize.large)
                        } else {
                            IconButton(if (expanded) Icons.ChevronDown else Icons.ChevronRight, onToggle, Modifier.size(24.dp), tooltip = if (expanded) "Collapse group" else "Expand group", size = 24.dp)
                            Glyph(if (expanded) Icons.FolderOpen else Icons.Folder, colors.textSecondary, size = IconSize.large)
                            Label(group.name, Modifier.weight(1f), color = colors.textPrimary, style = Type.bodyMedium, tooltipWhenTruncated = true)
                            if (hovered) {
                                IconButton(Icons.More, { menuOpen = true }, Modifier.size(24.dp), tooltip = "Group actions", size = 24.dp)
                            } else {
                                CountBadge(memberCount)
                            }
                            StatusDot(status)
                        }
                    }
                }
            }
            if (menuOpen) PopupMenu(groupMenu(actions), onDismiss = { menuOpen = false })
        }
    }
}
