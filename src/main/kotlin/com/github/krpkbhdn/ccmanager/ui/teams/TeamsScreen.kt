package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val listWidth = 260.dp
private const val DELEGATION_HINT = "A task runs one lead session. Every other agent is a subagent the lead hands work to, chosen by the agent's description."

/** Teams of role agents: the team list on the left, the selected team's compact roster on the right. */
@Composable
fun TeamsScreen(teams: TeamsState, projects: List<Project>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize()) {
        TeamList(teams, Modifier.width(listWidth).fillMaxHeight().background(colors.sidebar))
        VerticalRule()
        val team = teams.selected
        if (team == null) {
            EmptyState(Icons.Users, "No team yet", "Create a team of role agents, then start tasks with it from a project.", Modifier.weight(1f)) {
                AppButton("New team", teams::newTeam, variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
            }
        } else {
            Roster(teams, team, projects, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TeamList(teams: TeamsState, modifier: Modifier) {
    val list = rememberLazyListState()
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Teams", Modifier.weight(1f), style = Type.heading)
            CountBadge(teams.teams.size)
            IconButton(Icons.Plus, teams::newTeam, tooltip = "New team")
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(teams.teams, key = { it.id }) { team -> TeamCard(team, team.id == teams.selected?.id) { teams.select(team) } }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun TeamCard(team: Team, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    val edge = if (selected) colors.accent.copy(alpha = Alpha.GLOW) else colors.border
    Pressable(onClick, Modifier.fillMaxWidth().border(1.dp, edge, shape), shape, if (selected) colors.surfaceSelected else colors.surface, if (selected) colors.surfaceSelected else colors.surfaceHover) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(team.name, style = Type.bodyMedium, tooltipWhenTruncated = true)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                team.roles.take(MAX_AVATARS).forEachIndexed { slot, role -> RoleAvatar(role.name(), slot, 24.dp) }
                Label(if (team.roles.size == 1) "1 agent" else "${team.roles.size} agents", Modifier.padding(start = Spacing.xs), color = colors.textTertiary, style = Type.caption)
            }
        }
    }
}

private const val MAX_AVATARS = 4

@Composable
private fun Roster(teams: TeamsState, team: Team, projects: List<Project>, modifier: Modifier) {
    val list = rememberLazyListState()
    Column(modifier.fillMaxHeight()) {
        TeamHeader(teams, team, projects)
        HorizontalRule()
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Agents", style = Type.heading)
            CountBadge(team.roles.size)
            Label(DELEGATION_HINT, Modifier.weight(1f).padding(horizontal = Spacing.sm), color = colors.textTertiary, style = Type.caption, maxLines = 2)
            AppButton("New agent", { teams.newRole(team) }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, compact = true)
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                itemsIndexed(team.roles, key = { _, role -> role.id }) { slot, role -> RoleRow(role, slot, rowActions(teams, team, role)) }
                if (team.subagents().isEmpty()) item(key = "empty") { RosterEmptyHint { teams.newRole(team) } }
            }
            ThinScrollbar(list)
        }
    }
}

private fun rowActions(teams: TeamsState, team: Team, role: TeamRole) = RoleRowActions(
    onEdit = { teams.editRole(team, role) },
    onDuplicate = { teams.duplicateRole(team, role) },
    onDelete = { teams.requestDeleteRole(team, role) },
    onMoveUp = { teams.moveRole(team, role, -1) },
    onMoveDown = { teams.moveRole(team, role, 1) },
)

@Composable
private fun TeamHeader(teams: TeamsState, team: Team, projects: List<Project>) {
    val name = remember(team.id, team.name) { TextFieldState(team.name) }
    val changed = name.text.toString().trim().let { it.isNotEmpty() && it != team.name }
    val rename = { if (changed) teams.renameTeam(team, name.text.toString().trim()) }
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(name, Modifier.weight(1f), placeholder = "Team name", onSubmit = rename)
        if (changed) AppButton("Rename", rename, variant = ButtonVariant.PRIMARY, icon = Icons.Check)
        DefaultForProjects(teams, team, projects)
        IconButton(Icons.Copy, { teams.duplicateTeam(team) }, tooltip = "Duplicate team")
        IconButton(Icons.Trash, { teams.requestDeleteTeam(team) }, tooltip = "Delete team", tint = colors.danger)
    }
}

@Composable
private fun DefaultForProjects(teams: TeamsState, team: Team, projects: List<Project>) {
    var open by remember { mutableStateOf(false) }
    val count = projects.count { teams.projectDefaults[it.id] == team.id }
    Box {
        AppButton(if (count == 0) "Default for…" else "Default in $count", { open = !open }, icon = Icons.Folder, enabled = projects.isNotEmpty(), tooltip = "Projects that start tasks with this team by default")
        if (open) PopupMenu(projects.map { project -> MenuEntry(project.name, if (teams.projectDefaults[project.id] == team.id) Icons.Check else null) { teams.toggleProjectDefault(team, project.id) } }, { open = false }, alignEnd = true)
    }
}
