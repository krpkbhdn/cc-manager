package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.github.krpkbhdn.ccmanager.claude.proposal.FormProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.FormAnswer
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.MonoBlock
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.Desktop
import java.net.URI

private const val HINTS = "Tab moves · Ctrl+Enter accepts"

/** MCP elicitation: a link to open for mode "url", otherwise a form generated from the requested schema. Errors show after the first Accept. */
@Composable
internal fun FormDialog(proposal: FormProposal, title: FrameTitle, context: ProposalContext) {
    val urlMode = proposal.mode() == "url"
    val draft = remember { context.draft { FormDraft(if (urlMode) emptyList() else formFields(proposal.requestedSchema())) } }
    val firstText = draft.fields.indexOfFirst { it.kind != FieldKind.CHOICE && it.kind != FieldKind.BOOLEAN }
    val focus = remember { FocusRequester() }
    val accept = { draft.accept()?.let(context::answer) ?: Unit }
    ProposalDialogFrame(
        title, context, FORM_WIDTH, focus, HINTS,
        actions = {
            DialogButton("Cancel", { context.answer(FormAnswer.cancel()) }, variant = ButtonVariant.GHOST)
            DialogButton("Decline", { context.answer(FormAnswer.decline()) })
            DialogButton("Accept", { accept() }, if (firstText < 0) Modifier.focusRequester(focus) else Modifier, ButtonVariant.PRIMARY)
        },
        onCtrlEnter = { accept() },
    ) {
        Label(proposal.serverName(), color = colors.textTertiary, style = Type.overline)
        if (proposal.message().isNotBlank()) Label(proposal.message(), maxLines = Int.MAX_VALUE)
        if (urlMode) UrlRow(proposal.url())
        draft.fields.forEachIndexed { index, field ->
            FieldRow(field, draft.values[field.key].orEmpty(), draft.errors[field.key].takeIf { draft.showErrors }, focus.takeIf { index == firstText }) { draft.values[field.key] = it }
        }
    }
}

@Composable
private fun UrlRow(url: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.weight(1f)) { MonoBlock(url) }
        SecondaryButton("Open", { openInBrowser(url) }, icon = Icons.ExternalLink, enabled = isWebAddress(url))
    }
}

private fun isWebAddress(url: String) = url.startsWith("https://") || url.startsWith("http://")

private fun openInBrowser(url: String) {
    runCatching { Desktop.getDesktop().browse(URI(url)) }
}

@Composable
private fun FieldRow(field: FormField, value: String, error: String?, focus: FocusRequester?, onChange: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        if (field.kind == FieldKind.BOOLEAN) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppCheckbox(value.toBoolean(), { onChange(it.toString()) })
                Label(field.label, style = Type.bodyMedium)
            }
        } else {
            Label(if (field.required) "${field.label} *" else field.label, style = Type.bodyMedium)
        }
        if (field.help.isNotBlank()) Label(field.help, color = colors.textSecondary, style = Type.small, maxLines = Int.MAX_VALUE)
        when (field.kind) {
            FieldKind.CHOICE -> SegmentedControl(field.options.zip(field.optionLabels).map { (value, label) -> Segment(value, label) }, value, onChange)
            FieldKind.BOOLEAN -> Unit
            else -> TextInput(value, onChange, field.kind != FieldKind.TEXT, focus)
        }
        error?.let { Label(it, color = colors.danger, style = Type.small) }
    }
}

/** Text field that mirrors a string value held outside, so the form draft stays the single source of truth. */
@Composable
private fun TextInput(value: String, onChange: (String) -> Unit, numeric: Boolean, focus: FocusRequester?) {
    val state = remember { TextFieldState(value) }
    LaunchedEffect(state) { snapshotFlow { state.text.toString() }.collect(onChange) }
    AppTextField(state, Modifier.fillMaxWidth(), placeholder = if (numeric) "Number" else "", mono = numeric, focusRequester = focus)
}
