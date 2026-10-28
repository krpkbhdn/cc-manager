package com.github.krpkbhdn.ccmanager.ui.prompts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val PREVIEW_LINES = 3
private const val NO_TEMPLATES_HINT = "Save task texts in the Prompt Library, then pick them here."
private const val PROJECT_DEFAULT_TEAM = "Project's team"
private const val THIS_PROJECT = "This project"

/** "Choose a prompt": cards of the library prompts available in [projectId]; click or Enter picks one through [onChoose]. */
@Composable
fun TemplatePickerDialog(templates: List<PromptTemplate>, projectId: Long, teams: List<Team>, onChoose: (PromptTemplate) -> Unit, onDismiss: () -> Unit) {
    val ordered = remember(templates, projectId) { pickerOrder(templates, projectId) }
    PickerDialog("Choose a prompt", Icons.Message, ordered, NO_TEMPLATES_HINT, onChoose, onDismiss) { template, selected, onHover ->
        val team = teams.firstOrNull { it.id == template.defaultTeamId }
        PickerCard(selected, onHover, { onChoose(template) }) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(template.name, Modifier.weight(1f, fill = false), style = Type.bodyMedium, tooltipWhenTruncated = true)
                if (template.projectId == projectId) Chip(THIS_PROJECT, colors.accent)
                Chip(team?.name ?: PROJECT_DEFAULT_TEAM, colors.textSecondary)
            }
            if (template.description.isNotBlank()) Label(template.description, color = colors.textSecondary, style = Type.small, maxLines = 2)
            Label(template.text, color = colors.textTertiary, style = Type.caption, maxLines = PREVIEW_LINES)
        }
    }
}
