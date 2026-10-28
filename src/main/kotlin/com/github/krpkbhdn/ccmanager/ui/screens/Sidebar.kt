package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.state.GroupDialogRequest
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Shortcut
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val expandedWidth = 264.dp
private val railWidth = 60.dp

/** Left navigation: quick switch, overview and projects; collapses to an avatar rail. */
@Composable
fun Sidebar(state: AppState, modifier: Modifier = Modifier, forceCollapsed: Boolean = false) {
    val collapsed = state.settings.sidebarCollapsed || forceCollapsed
    val width by animateDpAsState(if (collapsed) railWidth else expandedWidth, Motion.slow(), label = "sidebarWidth")
    Row(modifier.fillMaxHeight()) {
        Column(Modifier.width(width).fillMaxHeight().background(colors.sidebar).padding(horizontal = if (collapsed) Spacing.sm else Spacing.md)) {
            SidebarHeader(collapsed, forceCollapsed, state)
            NavItem(Icons.Grid, "Overview", state.route == Route.Overview, collapsed, state.live.sessions.size) { state.navigate(Route.Overview) }
            NavItem(Icons.Layers, "All sessions", state.route == Route.Sessions, collapsed, state.live.sessions.count { it.status == AgentStatus.NEEDS_ACTION }) { state.navigate(Route.Sessions) }
            NavItem(Icons.Sparkles, "Assistant", state.route == Route.Assistant, collapsed, 0) { state.navigate(Route.Assistant) }
            NavItem(Icons.Message, "Prompt Library", state.route == Route.Prompts, collapsed, 0) { state.navigate(Route.Prompts) }
            NavItem(Icons.Users, "Teams", state.route == Route.Teams, collapsed, 0) { state.navigate(Route.Teams) }
            ExtensionsGroup(state, collapsed)
            ProjectsSection(state, collapsed, Modifier.weight(1f))
        }
        VerticalRule()
    }
}

@Composable
private fun SidebarHeader(collapsed: Boolean, locked: Boolean, state: AppState) {
    Column(Modifier.padding(top = Spacing.md, bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (collapsed) {
            if (!locked) {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    IconButton(Icons.PanelLeftOpen, state::toggleSidebar, tooltip = "Expand sidebar", hint = AppShortcut.TOGGLE_SIDEBAR.hint())
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(Icons.Search, { state.paletteOpen = true }, tooltip = "Search or jump to", hint = AppShortcut.PALETTE.hint())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SearchTrigger({ state.paletteOpen = true }, Modifier.weight(1f))
                IconButton(Icons.PanelLeftClose, state::toggleSidebar, tooltip = "Collapse sidebar", hint = AppShortcut.TOGGLE_SIDEBAR.hint())
            }
        }
    }
}

@Composable
private fun SearchTrigger(onClick: () -> Unit, modifier: Modifier) {
    Pressable(onClick, modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = colors.hoverOverlay) {
        Row(Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.sm - 1.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(Icons.Search, colors.textTertiary)
            Label("Search or jump to", Modifier.weight(1f), color = colors.textTertiary, style = Type.small)
            Shortcut(AppShortcut.PALETTE)
        }
    }
}

@Composable
internal fun NavItem(icon: ImageVector, label: String, selected: Boolean, collapsed: Boolean, count: Int, onClick: () -> Unit) {
    val tint = if (selected) colors.accent else colors.textSecondary
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (selected) colors.accentSoft else Color.Transparent) {
        Row(
            Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.sm - 1.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(Spacing.md - 2.dp),
        ) {
            Glyph(icon, tint, size = IconSize.large)
            if (!collapsed) {
                Label(label, Modifier.weight(1f), color = if (selected) colors.textPrimary else colors.textSecondary, style = Type.bodyMedium)
                if (count > 0) CountBadge(count)
            }
        }
    }
}

@Composable
private fun ProjectsSection(state: AppState, collapsed: Boolean, modifier: Modifier) {
    val list: LazyListState = rememberLazyListState()
    Column(modifier) {
        if (!collapsed) {
            Row(Modifier.fillMaxWidth().padding(start = Spacing.sm, top = Spacing.lg, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                Label("Projects", Modifier.weight(1f), color = colors.textTertiary, style = Type.overline)
                CountBadge(state.projects.topLevelCount)
            }
        } else {
            HorizontalRule(Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.sm))
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.animateContentSize(), list, verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                state.projects.groups.forEach { group ->
                    val members = state.projects.membersOf(group.id)
                    val open = collapsed || group.id !in state.projects.foldedGroups
                    item(key = group.id) {
                        GroupRow(
                            group = group,
                            status = state.live.statusOf(group.id),
                            memberCount = members.size,
                            expanded = open,
                            selected = state.route == Route.Project(group.id),
                            collapsed = collapsed,
                            actions = state.groupActions(group),
                            onToggle = { state.projects.toggleFolded(group.id) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                    if (open) items(members, key = { it.id }) { SidebarProject(state, it, collapsed, Modifier.animateItem().padding(start = if (collapsed) 0.dp else Spacing.lg)) }
                }
                items(state.projects.ungrouped, key = { it.id }) { SidebarProject(state, it, collapsed, Modifier.animateItem()) }
                item(key = "add") { AddProjectRow(collapsed, state::requestAddProject, Modifier.animateItem()) }
                item(key = "add-group") { AddGroupRow(collapsed, { state.projects.groupDialog = GroupDialogRequest(null) }, Modifier.animateItem()) }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun SidebarProject(state: AppState, project: Project, collapsed: Boolean, modifier: Modifier) {
    ProjectRow(
        project = project,
        status = state.live.statusOf(project.id),
        sessions = state.live.of(project.id),
        selected = state.route == Route.Project(project.id),
        collapsed = collapsed,
        gitSummary = state.gitSummaryOf(project.id),
        actions = state.projectActions(project),
        modifier = modifier,
        groupName = project.groupId?.let(state.projects::nameOf),
    )
}

@Composable
private fun AddProjectRow(collapsed: Boolean, onClick: () -> Unit, modifier: Modifier) =
    AddRow(Icons.Plus, "Add project", collapsed, onClick, modifier) { Shortcut(AppShortcut.ADD_PROJECT) }

@Composable
private fun AddGroupRow(collapsed: Boolean, onClick: () -> Unit, modifier: Modifier) =
    AddRow(Icons.FolderPlus, "New group", collapsed, onClick, modifier)

@Composable
private fun AddRow(icon: ImageVector, label: String, collapsed: Boolean, onClick: () -> Unit, modifier: Modifier, trailing: @Composable () -> Unit = {}) {
    Pressable(onClick, modifier.fillMaxWidth().padding(top = Spacing.xs), shape = RoundedCornerShape(Radius.md)) {
        Row(
            Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.sm).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (collapsed) Arrangement.Center else Arrangement.spacedBy(Spacing.md - 2.dp),
        ) {
            Glyph(icon, colors.textSecondary, size = IconSize.large)
            if (!collapsed) {
                Label(label, Modifier.weight(1f), color = colors.textSecondary, style = Type.bodyMedium)
                trailing()
            }
        }
    }
}

private class ExtensionNav(val icon: ImageVector, val label: String, val route: Route)

private val extensionNavItems = listOf(
    ExtensionNav(Icons.Plug, "Plugins", Route.Plugins),
    ExtensionNav(Icons.Server, "MCP servers", Route.Mcp),
    ExtensionNav(Icons.Book, "Skills", Route.Skills),
)

@Composable
private fun ExtensionsGroup(state: AppState, collapsed: Boolean) {
    if (collapsed) {
        HorizontalRule(Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.sm))
    } else {
        Label("Extensions", Modifier.padding(start = Spacing.sm, top = Spacing.lg, bottom = Spacing.xs), color = colors.textTertiary, style = Type.overline)
    }
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        extensionNavItems.forEach { item -> NavItem(item.icon, item.label, state.route == item.route, collapsed, 0) { state.navigate(item.route) } }
    }
}
