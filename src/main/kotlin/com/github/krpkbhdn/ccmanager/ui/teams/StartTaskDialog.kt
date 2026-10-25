package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp
private const val TASK_HINT = "Claude starts one lead session in the project with this text as its first message. The lead delegates to the roles below."

class StartTaskActions(val onStart: (team: Team, task: String, makeDefault: Boolean) -> Unit, val onManageTeams: () -> Unit, val onDismiss: () -> Unit)

/** Pick a team, describe the task, and see which roles the lead will have; Ctrl+Enter starts it. */
@Composable
fun StartTaskDialog(project: Project, teams: TeamsState, actions: StartTaskActions) {
    var selected by remember { mutableStateOf(teams.defaultTeamOf(project.id)?.id) }
    var makeDefault by remember { mutableStateOf(false) }
    val task = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val team = selected?.let(teams::find)
    val start = { if (team != null && task.text.isNotBlank()) actions.onStart(team, task.text.toString().trim(), makeDefault) }
    ModalHost(actions.onDismiss, width = dialogWidth, takeFocus = false, hasInput = { task.text.isNotEmpty() }) {
        DialogBody("Start task in ${project.name}", Icons.Users, colors.accent) {
            if (teams.teams.isEmpty()) {
                NoTeams(actions)
            } else {
                Label("Team", color = colors.textSecondary, style = Type.smallMedium)
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                    teams.teams.forEach { TeamOption(it, it.id == selected) { _ -> selected = it.id } }
                }
                team?.let { RolesPreview(it) }
                Label("Task", color = colors.textSecondary, style = Type.smallMedium)
                AppTextArea(task, Modifier.fillMaxWidth(), placeholder = "Describe what the team should do", focusRequester = focus, onSubmit = start)
                Label(TASK_HINT, color = colors.textTertiary, style = Type.caption, maxLines = 2)
                DefaultTeamToggle(makeDefault) { makeDefault = it }
                DialogActions("Start task", team != null && task.text.isNotBlank(), false, start, actions.onDismiss)
            }
        }
    }
}

@Composable
private fun NoTeams(actions: StartTaskActions) {
    Label("There is no team yet. Create one with its roles first.", color = colors.textSecondary, maxLines = 3)
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
        AppButton("Cancel", actions.onDismiss)
        AppButton("Manage teams", { actions.onDismiss(); actions.onManageTeams() }, icon = Icons.Users)
    }
}

@Composable
private fun TeamOption(team: Team, selected: Boolean, onSelect: (Team) -> Unit) {
    Pressable({ onSelect(team) }, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm), if (selected) colors.accentSoft else Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.xs + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.width(16.dp)) { if (selected) Glyph(Icons.Check, colors.accent) }
            Label(team.name, Modifier.weight(1f), style = if (selected) Type.bodyMedium else Type.body)
            Label("${team.roles.size} roles", color = colors.textTertiary, style = Type.caption)
        }
    }
}

@Composable
private fun RolesPreview(team: Team) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label("Roles available to the lead", color = colors.textSecondary, style = Type.smallMedium)
        team.roles.forEachIndexed { slot, role ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                RoleAvatar(role.name(), slot, 22.dp)
                Label(role.name(), Modifier.width(96.dp), style = Type.smallMedium)
                Label(if (role.lead()) "Leads the session" else role.draft.description, Modifier.weight(1f), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
                RoleChip(role, slot)
            }
        }
    }
}

@Composable
private fun DefaultTeamToggle(checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppCheckbox(checked, onChange)
        Label("Use this team by default in this project", color = colors.textSecondary, style = Type.small)
    }
}
