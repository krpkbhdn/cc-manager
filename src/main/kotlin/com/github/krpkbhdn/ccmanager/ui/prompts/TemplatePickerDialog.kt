package com.github.krpkbhdn.ccmanager.ui.prompts

import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.focusable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp
private const val PREVIEW_LINES = 3
private const val NO_TEMPLATES_HINT = "Save task texts on the Templates screen, then pick them here."
private const val KEYS_HINT = "Up and Down to move, Enter to choose, Esc to close."
private const val PROJECT_DEFAULT_TEAM = "Project's team"
private const val THIS_PROJECT = "This project"

/** "Choose a template": cards of the templates available in [projectId]; click or Enter picks one through [onChoose]. */
@Composable
fun TemplatePickerDialog(templates: List<PromptTemplate>, projectId: Long, teams: List<Team>, onChoose: (PromptTemplate) -> Unit, onDismiss: () -> Unit) {
    val ordered = remember(templates, projectId) { pickerOrder(templates, projectId) }
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val choose = { ordered.getOrNull(selected)?.let(onChoose); Unit }
    ModalHost(onDismiss, width = dialogWidth, takeFocus = false) {
        DialogBody("Choose a template", Icons.Message, colors.accent) {
            Column(
                Modifier.fillMaxWidth().focusRequester(focus).focusable().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> { selected = movedSelection(selected, 1, ordered.size); true }
                        Key.DirectionUp -> { selected = movedSelection(selected, -1, ordered.size); true }
                        Key.Enter, Key.NumPadEnter -> { choose(); true }
                        else -> false
                    }
                },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (ordered.isEmpty()) Label(NO_TEMPLATES_HINT, color = colors.textTertiary, maxLines = 2)
                ordered.forEachIndexed { index, template ->
                    TemplateCard(template, projectId, teams, index == selected, { selected = index }, { onChoose(template) })
                }
                if (ordered.isNotEmpty()) Label(KEYS_HINT, color = colors.textTertiary, style = Type.caption)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TemplateCard(template: PromptTemplate, projectId: Long, teams: List<Team>, selected: Boolean, onHover: () -> Unit, onChoose: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) inView.bringIntoView() }
    val team = teams.firstOrNull { it.id == template.defaultTeamId }
    Pressable(
        onChoose,
        Modifier.fillMaxWidth().bringIntoViewRequester(inView).border(1.dp, if (selected) colors.accent else colors.border, shape),
        shape,
        base = if (selected) colors.surfaceHover else colors.surface,
        hover = colors.surfaceHover,
    ) { hovered ->
        LaunchedEffect(hovered) { if (hovered) onHover() }
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
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
