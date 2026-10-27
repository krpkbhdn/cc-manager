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
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val EMPTY_HINT = "Save task texts you use often, then start them in any project from the start-task dialog."

/** Prompt templates: list with create, edit, delete and Run (opens the start-task dialog in a chosen project). */
@Composable
fun PromptsScreen(prompts: PromptsState, projects: List<Project>, teams: List<Team>, onRun: (PromptTemplate, Project) -> Unit, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Templates", Modifier.weight(1f), style = Type.heading)
            CountBadge(prompts.templates.size)
            AppButton("New template", { prompts.openDialog() }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, compact = true)
        }
        HorizontalRule()
        if (prompts.templates.isEmpty()) {
            EmptyState(Icons.Message, "No templates yet", EMPTY_HINT, Modifier.weight(1f)) {
                AppButton("New template", { prompts.openDialog() }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
            }
        } else {
            Box(Modifier.weight(1f)) {
                LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(prompts.templates, key = { it.id }) { template -> TemplateRow(template, prompts, projects, teams, onRun) }
                }
                ThinScrollbar(list)
            }
        }
    }
}

@Composable
private fun TemplateRow(template: PromptTemplate, prompts: PromptsState, projects: List<Project>, teams: List<Team>, onRun: (PromptTemplate, Project) -> Unit) {
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
            Label(template.text.replace('\n', ' '), color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
            Label(details, color = colors.textTertiary, style = Type.caption)
        }
        RunButton(template, bound, projects, onRun)
        IconButton(Icons.Edit, { prompts.openDialog(template) }, tooltip = "Edit template")
        IconButton(Icons.Trash, { prompts.requestDelete(template) }, tooltip = "Delete template", tint = colors.danger)
    }
}

@Composable
private fun RunButton(template: PromptTemplate, bound: Project?, projects: List<Project>, onRun: (PromptTemplate, Project) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton("Run", { if (bound != null) onRun(template, bound) else open = !open }, icon = Icons.Play, enabled = projects.isNotEmpty(), tooltip = "Start a task from this template")
        if (open) PopupMenu(projects.map { project -> MenuEntry(project.name, Icons.Folder) { onRun(template, project) } }, { open = false }, alignEnd = true)
    }
}
