package com.github.krpkbhdn.ccmanager.ui.queue

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderSubstitution
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.CountDot
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val panelWidth = 400.dp
private const val QUEUE_NOTE = "A prompt is typed as soon as Claude is free, then one more after each finished turn. Nothing is sent while you are typing, while Claude waits for you, or right after an Esc interrupt."

/** Tab strip button of a live session: opens the panel with its queued prompts. */
@Composable
internal fun QueueControl(state: AppState, project: Project, ccmSessionId: String) {
    val queue = state.queue
    val count = queue.of(ccmSessionId).size
    Box {
        AppButton(
            "Queue", { queue.panelSessionId = if (queue.panelSessionId == ccmSessionId) null else ccmSessionId },
            variant = ButtonVariant.SECONDARY, icon = Icons.List, compact = true, tooltip = "Prompts to send when Claude is free",
            trailing = if (count > 0) ({ CountDot(count) }) else null,
        )
        if (queue.panelSessionId == ccmSessionId) QueuePanel(state, project, ccmSessionId)
    }
}

@Composable
private fun QueuePanel(state: AppState, project: Project, ccmSessionId: String) {
    val queue = state.queue
    val draft = rememberTextFieldState()
    val add = {
        queue.add(ccmSessionId, draft.text.toString())
        draft.edit { replace(0, length, "") }
    }
    AnchoredPopup({ queue.panelSessionId = null }, alignEnd = true, modifier = Modifier.width(panelWidth)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Label("Prompt queue", Modifier.weight(1f), style = Type.heading)
                if (queue.of(ccmSessionId).isNotEmpty()) AppButton("Clear", { queue.clear(ccmSessionId) }, icon = Icons.Trash, compact = true)
            }
            queue.of(ccmSessionId).forEachIndexed { index, prompt ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Label("${index + 1}.", color = colors.textTertiary, style = Type.numericCaption)
                    Label(prompt.text, Modifier.weight(1f), maxLines = 2, tooltipWhenTruncated = true)
                    IconButton(Icons.Trash, { queue.remove(ccmSessionId, prompt.id) }, tooltip = "Remove from the queue", tint = colors.danger)
                }
            }
            AppTextArea(draft, Modifier.fillMaxWidth(), placeholder = "Prompt to send next", onSubmit = add)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppButton("Add", add, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, compact = true, enabled = draft.text.isNotBlank())
                TemplateMenu(state.prompts.forProject(project.id)) { template ->
                    queue.add(ccmSessionId, PlaceholderSubstitution.apply(template.text, state.placeholderValuesOf(project)))
                }
            }
            Label(QUEUE_NOTE, color = colors.textTertiary, style = Type.caption, maxLines = 3)
        }
    }
}

@Composable
private fun TemplateMenu(templates: List<PromptTemplate>, onSelect: (PromptTemplate) -> Unit) {
    if (templates.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton("From library", { open = !open }, icon = Icons.Message, compact = true, tooltip = "Queue a saved prompt from the library")
        if (open) PopupMenu(templates.map { template -> MenuEntry(template.name) { onSelect(template) } }, { open = false })
    }
}
