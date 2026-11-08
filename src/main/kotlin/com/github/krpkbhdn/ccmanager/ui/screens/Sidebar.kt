package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Shortcut
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.SidebarSection
import com.github.krpkbhdn.ccmanager.ui.state.sidebarProjectOrder
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
        val edge = if (collapsed) Spacing.sm else Spacing.md
        Column(Modifier.width(width).fillMaxHeight().background(colors.sidebar)) {
            Column(Modifier.padding(horizontal = edge)) {
                SidebarHeader(collapsed, forceCollapsed, state)
                NavItem(Icons.Grid, "Overview", state.route == Route.Overview, collapsed, state.live.sessions.size) { state.navigate(Route.Overview) }
                NavItem(Icons.Layers, "Sessions", state.route == Route.Sessions, collapsed, state.live.sessions.count { it.status == AgentStatus.NEEDS_ACTION }) { state.navigate(Route.Sessions) }
                NavItem(Icons.Sparkles, "Assistant", state.route == Route.Assistant, collapsed, 0) { state.navigate(Route.Assistant) }
                HorizontalRule(Modifier.padding(vertical = Spacing.sm, horizontal = Spacing.sm))
                navSections.forEach { NavSectionGroup(state, it, collapsed) }
            }
            ProjectsSection(state, collapsed, edge, Modifier.weight(1f))
            HorizontalRule(Modifier.padding(horizontal = edge + Spacing.sm))
            Box(Modifier.padding(start = edge, end = edge, top = Spacing.sm, bottom = Spacing.lg)) {
                NavItem(Icons.Settings, "Settings", state.settingsDialog.open, collapsed, 0) { state.settingsDialog.show() }
            }
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
                    IconButton(Icons.PanelLeftOpen, state.prefs::toggleSidebar, tooltip = "Expand sidebar", hint = AppShortcut.TOGGLE_SIDEBAR.hint())
                }
            }
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                IconButton(Icons.Search, { state.paletteOpen = true }, tooltip = "Search or jump to", hint = AppShortcut.PALETTE.hint())
            }
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                SearchTrigger({ state.paletteOpen = true }, Modifier.weight(1f))
                IconButton(Icons.PanelLeftClose, state.prefs::toggleSidebar, tooltip = "Collapse sidebar", hint = AppShortcut.TOGGLE_SIDEBAR.hint())
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
private fun ProjectsSection(state: AppState, collapsed: Boolean, edge: Dp, modifier: Modifier) {
    val list: LazyListState = rememberLazyListState()
    val folded = !collapsed && SidebarSection.PROJECTS.name in state.settings.foldedSections
    Column(modifier) {
        if (!collapsed) {
            SectionHeader(SidebarSection.PROJECTS.title, folded, { state.prefs.toggleSectionFolded(SidebarSection.PROJECTS.name) }, Modifier.padding(start = edge, end = edge, top = Spacing.lg)) {
                CountBadge(state.projects.topLevelCount)
            }
        } else {
            HorizontalRule(Modifier.padding(vertical = Spacing.sm, horizontal = edge + Spacing.sm))
        }
        if (!folded) Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.animateContentSize(), list, contentPadding = PaddingValues(start = edge, end = if (collapsed) edge else edge + Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                val folded = state.settings.foldedGroups
                items(sidebarProjectOrder(state.projects.groups, state.projects::membersOf, state.projects.ungrouped, folded), key = { it.id }) { project ->
                    if (project.group) {
                        val members = state.projects.membersOf(project.id)
                        val open = project.id !in folded
                        GroupRow(
                            group = project,
                            status = state.live.statusOf(project.id),
                            memberCount = members.size,
                            expanded = open,
                            selected = state.route == Route.Project(project.id),
                            collapsed = collapsed,
                            actions = state.groupActions(project),
                            onToggle = { state.prefs.toggleGroupFolded(project.id) },
                            flyout = groupFlyout(state, project, members, open) { state.prefs.toggleGroupFolded(project.id) },
                            modifier = Modifier.animateItem(),
                        )
                    } else {
                        val indent = if (project.groupId == null || collapsed) 0.dp else Spacing.lg
                        SidebarProject(state, project, collapsed, Modifier.animateItem().padding(start = indent))
                    }
                }
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
        gitSummary = state.gits.summaryOf(project.id),
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

private fun groupFlyout(state: AppState, group: Project, members: List<Project>, open: Boolean, onToggle: () -> Unit): List<MenuEntry> =
    listOf(MenuEntry(group.name, Icons.FolderOpen) { state.navigate(Route.Project(group.id)) }) +
        members.map { member -> MenuEntry(member.name, status = state.live.statusOf(member.id)) { state.navigate(Route.Project(member.id)) } } +
        MenuEntry(if (open) "Collapse group" else "Expand group", if (open) Icons.ChevronDown else Icons.ChevronRight, dividerAbove = true, onSelect = onToggle)

@Composable
private fun SectionHeader(title: String, folded: Boolean, onToggle: () -> Unit, modifier: Modifier, trailing: @Composable () -> Unit = {}) {
    Pressable(onToggle, modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md)) {
        Row(Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.xs, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Glyph(if (folded) Icons.ChevronRight else Icons.ChevronDown, colors.textTertiary)
            Label(title, Modifier.weight(1f), color = colors.textTertiary, style = Type.overline)
            trailing()
        }
    }
}

@Composable
private fun NavSectionGroup(state: AppState, nav: NavSection, collapsed: Boolean) {
    if (collapsed) {
        val entries = nav.entries.map { entry -> MenuEntry(entry.label, entry.icon) { state.navigate(entry.route) } }
        HoverFlyout(entries) { NavItem(nav.icon, nav.section.title, nav.entries.any { state.route == it.route }, true, 0) {} }
        return
    }
    val folded = nav.section.name in state.settings.foldedSections
    SectionHeader(nav.section.title, folded, { state.prefs.toggleSectionFolded(nav.section.name) }, Modifier)
    if (!folded) Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        nav.entries.forEach { entry -> NavItem(entry.icon, entry.label, state.route == entry.route, false, 0) { state.navigate(entry.route) } }
    }
}
