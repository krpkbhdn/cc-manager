package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderSubstitution
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderValues
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.prompts.TemplatePickerDialog
import com.github.krpkbhdn.ccmanager.ui.prompts.teamForTemplate
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp
private const val NO_TEAM = "No team"
private const val NO_TEAM_NOTE = "The task runs in a plain Claude session without an agent team."
private const val NO_TEAM_TASK_HINT = "Claude starts one session in the project with this text as its first message."
private const val TASK_HINT = "Claude starts one lead session in the project with this text as its first message. The lead delegates to the roles below."

/** Templates offered in the dialog, the one to apply at once, and the values their placeholders get. */
class TaskTemplates(val available: List<PromptTemplate>, val initialId: Long?, val values: PlaceholderValues)

class StartTaskActions(val onStart: (team: Team?, task: String, makeDefault: Boolean) -> Unit, val onDismiss: () -> Unit)

/** Pick a team (or none), describe the task, and see which roles the lead will have; Ctrl+Enter starts it. */
@Composable
fun StartTaskDialog(project: Project, teams: TeamsState, templates: TaskTemplates, actions: StartTaskActions) {
    var selected by remember { mutableStateOf(teams.defaultTeamOf(project.id)?.id) }
    var makeDefault by remember { mutableStateOf(false) }
    val task = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    var picking by remember { mutableStateOf(false) }
    val apply: (PromptTemplate) -> Unit = { template ->
        task.setTextAndPlaceCursorAtEnd(PlaceholderSubstitution.apply(template.text, templates.values))
        selected = teamForTemplate(template, teams.teams, selected?.let(teams::find))?.id
    }
    LaunchedEffect(Unit) {
        focus.requestFocus()
        templates.available.firstOrNull { it.id == templates.initialId }?.let(apply)
    }
    val team = selected?.let(teams::find)
    val start = { if (task.text.isNotBlank()) actions.onStart(team, task.text.toString().trim(), team != null && makeDefault) }
    ModalHost(actions.onDismiss, width = dialogWidth, takeFocus = false, hasInput = { task.text.isNotEmpty() }) {
        DialogBody("Start task in ${project.name}", Icons.Users, colors.accent) {
            Label("Team", color = colors.textSecondary, style = Type.smallMedium)
            TeamSelect(team, teams.teams) { selected = it?.id }
            if (team != null) RolesPreview(team) else Label(NO_TEAM_NOTE, color = colors.textTertiary, style = Type.caption, maxLines = 2)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("Task", Modifier.weight(1f), color = colors.textSecondary, style = Type.smallMedium)
                AppButton("From library", { picking = true }, icon = Icons.Message, compact = true, tooltip = "Fill the task from a saved prompt in the library")
            }
            AppTextArea(task, Modifier.fillMaxWidth(), placeholder = "Describe what to do", focusRequester = focus, onSubmit = start)
            Label(if (team != null) TASK_HINT else NO_TEAM_TASK_HINT, color = colors.textTertiary, style = Type.caption, maxLines = 2)
            if (team != null) DefaultTeamToggle(makeDefault) { makeDefault = it }
            DialogActions("Start task", task.text.isNotBlank(), false, start, actions.onDismiss)
        }
    }
    if (picking) TemplatePickerDialog(templates.available, project.id, teams.teams, { picking = false; apply(it); focus.requestFocus() }, { picking = false; focus.requestFocus() })
}

@Composable
private fun TeamSelect(selected: Team?, teams: List<Team>, onSelect: (Team?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(selected?.name ?: NO_TEAM, { open = !open }, variant = ButtonVariant.SECONDARY, icon = Icons.Users, tooltip = "Team for this task")
        if (open) {
            val entries = teams.map { MenuEntry(it.name, hint = "${it.roles.size} roles") { onSelect(it) } } + MenuEntry(NO_TEAM, dividerAbove = teams.isNotEmpty()) { onSelect(null) }
            PopupMenu(entries, { open = false })
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
