package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.screens.effortSegments
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 780.dp
private const val HEIGHT_SHARE = 0.85f
private const val PROMPT_MIN_LINES = 12
private const val PROMPT_MAX_LINES = 40
private const val DESCRIPTION_HINT = "The lead reads this to decide when to delegate to this agent, so say what work belongs here."
private const val LEAD_NOTE = "The lead runs as the main session: its model and effort start the session and its prompt steers the delegation. Make another agent the lead to change this."

/** Create or edit one agent of a team; Ctrl+Enter or Save stores it, Esc or Cancel closes it (asking first when there are edits). */
@Composable
fun RoleDialog(team: Team, dialog: RoleDialogState, teams: TeamsState) {
    val form = dialog.form
    val wasLead = remember { form.lead }
    var saveError by remember { mutableStateOf<String?>(null) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val save = { if (form.error == null) teams.saveRole(dialog) { saveError = it } }
    val existing = dialog.index?.let { team.roles.getOrNull(it) }
    ModalHost(teams::requestCloseDialog, width = dialogWidth, takeFocus = false) {
        BoxWithConstraints {
            Column(Modifier.heightIn(max = maxHeight * HEIGHT_SHARE)) {
                Label(existing?.let { "Edit ${it.name()}" } ?: "New agent in ${team.name}", Modifier.padding(Spacing.xl), style = Type.subtitle)
                HorizontalRule()
                Column(Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState()).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    RoleFields(form, wasLead, focus, save)
                }
                HorizontalRule()
                Footer(form, saveError, existing?.takeIf { !wasLead }?.let { { teams.closeDialog(); teams.requestDeleteRole(team, it) } }, save, teams::requestCloseDialog)
            }
        }
    }
}

@Composable
private fun RoleFields(form: RoleForm, wasLead: Boolean, focus: FocusRequester, save: () -> Unit) {
    Field("Name", "Lowercase letters, digits and dashes. The lead calls the agent by this name.") {
        AppTextField(form.name, Modifier.fillMaxWidth(), mono = true, placeholder = "developer", focusRequester = focus, onSubmit = save)
    }
    Field("When the lead delegates to this agent", DESCRIPTION_HINT) {
        AppTextArea(form.description, Modifier.fillMaxWidth(), placeholder = "Use to write or change code: implement a feature, fix a bug", onSubmit = save)
    }
    Field("Prompt", "Instructions the agent works by.") {
        AppTextArea(form.prompt, Modifier.fillMaxWidth(), placeholder = "You are a careful developer. ...", minLines = PROMPT_MIN_LINES, maxLines = PROMPT_MAX_LINES, onSubmit = save)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.Top) {
        Field("Model", null) { ModelDropdown(form.model, if (form.lead) "Claude's default model" else "Inherit the lead's model", { form.model = it }) }
        Field("Reasoning effort", null) { SegmentedControl(effortSegments(), form.effort, { form.effort = it }) }
    }
    Field("Tools", null) { ToolPicker(form.tools, { form.tools = it }) }
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppCheckbox(form.lead, { form.lead = it }, enabled = !wasLead)
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label("Lead of the team", style = Type.bodyMedium)
            Label(if (wasLead) LEAD_NOTE else "Taking the lead hands it over from the current lead.", color = colors.textTertiary, style = Type.caption, maxLines = 3)
        }
    }
}

@Composable
private fun Footer(form: RoleForm, saveError: String?, onDelete: (() -> Unit)?, onSave: () -> Unit, onCancel: () -> Unit) {
    val error = form.error ?: saveError
    Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        onDelete?.let { AppButton("Delete", it, variant = ButtonVariant.GHOST, icon = Icons.Trash) }
        Label(error.orEmpty(), Modifier.weight(1f), color = colors.danger, style = Type.small, maxLines = 2, tooltipWhenTruncated = true)
        AppButton("Cancel", onCancel)
        AppButton("Save", onSave, variant = ButtonVariant.PRIMARY, enabled = form.error == null)
    }
}

@Composable
private fun Field(title: String, hint: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(title, color = colors.textSecondary, style = Type.smallMedium)
        content()
        hint?.let { Label(it, color = colors.textTertiary, style = Type.caption, maxLines = 2) }
    }
}
