package com.github.krpkbhdn.ccmanager.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp
const val ALL_PROJECTS = "All projects"
const val NO_DEFAULT_TEAM = "No default team"
private const val PLACEHOLDER_HINT = "Placeholders: {project}, {branch}. Unknown ones and ones without a value stay as typed."

/** Create or edit one library prompt; the backend's validation messages are shown under the fields. */
@Composable
fun PromptDialog(form: PromptDialogState, prompts: PromptsState, projects: List<Project>, teams: List<Team>) {
    var error by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { prompts.save(form) { error = it } }
    ModalHost(prompts::closeDialog, width = dialogWidth, takeFocus = false, hasInput = { form.hasInput }) {
        DialogBody(if (form.existing == null) "New prompt" else "Edit prompt", Icons.Message, colors.accent) {
            Field("Name") { AppTextField(form.name, Modifier.fillMaxWidth(), placeholder = "Review the branch", focusRequester = focus, onSubmit = save) }
            Field("Description (optional)") { AppTextField(form.description, Modifier.fillMaxWidth(), placeholder = "What this prompt is for") }
            Field("Text") {
                AppTextArea(form.text, Modifier.fillMaxWidth(), placeholder = "Review {project} on {branch} and list the risks", onSubmit = save)
                Label(PLACEHOLDER_HINT, color = colors.textTertiary, style = Type.caption, maxLines = 2)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Field("Project") { ProjectSelect(form.projectId, projects) { form.projectId = it } }
                Field("Default team") { TeamSelect(form.teamId, teams) { form.teamId = it } }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(error.orEmpty(), Modifier.weight(1f), color = colors.danger, style = Type.small, maxLines = 2, tooltipWhenTruncated = true)
                AppButton("Cancel", prompts::closeDialog)
                AppButton("Save", save, variant = ButtonVariant.PRIMARY)
            }
        }
    }
}

@Composable
private fun ProjectSelect(selected: Long?, projects: List<Project>, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(projects.firstOrNull { it.id == selected }?.name ?: ALL_PROJECTS, { open = !open }, icon = Icons.Folder, tooltip = "Project the prompt belongs to")
        if (open) {
            val entries = listOf(MenuEntry(ALL_PROJECTS) { onSelect(null) }) + projects.map { MenuEntry(it.name, dividerAbove = it === projects.first()) { onSelect(it.id) } }
            PopupMenu(entries, { open = false })
        }
    }
}

@Composable
private fun TeamSelect(selected: Long?, teams: List<Team>, onSelect: (Long?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(teams.firstOrNull { it.id == selected }?.name ?: NO_DEFAULT_TEAM, { open = !open }, icon = Icons.Users, tooltip = "Team preselected when the prompt is used")
        if (open) {
            val entries = listOf(MenuEntry(NO_DEFAULT_TEAM) { onSelect(null) }) + teams.map { MenuEntry(it.name, dividerAbove = it === teams.first()) { onSelect(it.id) } }
            PopupMenu(entries, { open = false })
        }
    }
}

@Composable
private fun Field(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(title, color = colors.textSecondary, style = Type.smallMedium)
        content()
    }
}
