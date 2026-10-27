package com.github.krpkbhdn.ccmanager.ui.prompts

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val EMPTY_HINT = "Save task texts you use often, then start them in any project from the start-task dialog."

/** Prompt Library: list with create, edit, delete and Run (opens the start-task dialog in a chosen project). */
@Composable
fun PromptsScreen(prompts: PromptsState, projects: List<Project>, teams: List<Team>, teamOf: (Project) -> Team?, onRun: (PromptTemplate, Project) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    var choosingProjectFor by remember { mutableStateOf<PromptTemplate?>(null) }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Prompt Library", Modifier.weight(1f), style = Type.heading)
            CountBadge(prompts.templates.size)
            AppButton("New prompt", { prompts.openDialog() }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, compact = true)
        }
        HorizontalRule()
        if (prompts.templates.isEmpty()) {
            EmptyState(Icons.Message, "No prompts yet", EMPTY_HINT, Modifier.weight(1f)) {
                AppButton("New prompt", { prompts.openDialog() }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
            }
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(prompts.templates, key = { it.id }) { template -> TemplateRow(template, prompts, projects, teams, onRun) { choosingProjectFor = template } }
                }
                ThinScrollbar(list)
            }
        }
        choosingProjectFor?.let { template ->
            ProjectPickerDialog(projects, teamOf, { choosingProjectFor = null; onRun(template, it) }, { choosingProjectFor = null })
        }
    }
}

@Composable
private fun TemplateRow(template: PromptTemplate, prompts: PromptsState, projects: List<Project>, teams: List<Team>, onRun: (PromptTemplate, Project) -> Unit, onChooseProject: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    val bound = projects.firstOrNull { it.id == template.projectId }
    val team = teams.firstOrNull { it.id == template.defaultTeamId }
    val details = listOfNotNull(bound?.name ?: ALL_PROJECTS, team?.name).joinToString(" · ")
    Row(
        Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape).padding(Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Label(template.name, style = Type.bodyMedium, tooltipWhenTruncated = true)
            if (template.description.isNotBlank()) Label(template.description, style = Type.small, tooltipWhenTruncated = true)
            Label(template.text.replace('\n', ' '), color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
            Label(details, color = colors.textTertiary, style = Type.caption)
        }
        AppButton("Run", { if (bound != null) onRun(template, bound) else onChooseProject() }, icon = Icons.Play, enabled = projects.isNotEmpty(), tooltip = "Start a task from this prompt")
        IconButton(Icons.Edit, { prompts.openDialog(template) }, tooltip = "Edit prompt")
        IconButton(Icons.Trash, { prompts.requestDelete(template) }, tooltip = "Delete prompt", tint = colors.danger)
    }
}

