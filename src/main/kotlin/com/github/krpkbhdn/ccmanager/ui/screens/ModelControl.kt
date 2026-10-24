package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.ModelPreset
import com.github.krpkbhdn.ccmanager.ui.state.ModelTarget
import com.github.krpkbhdn.ccmanager.ui.state.effectiveModelLabel
import com.github.krpkbhdn.ccmanager.ui.state.isCustomModel
import com.github.krpkbhdn.ccmanager.ui.state.knownEffort
import com.github.krpkbhdn.ccmanager.ui.state.modelPresets
import com.github.krpkbhdn.ccmanager.ui.state.modelTarget
import com.github.krpkbhdn.ccmanager.ui.state.selectionLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val pickerWidth = 420.dp
private const val DEFAULT_EFFORT_LABEL = "Default"
private const val RUNNING_NOTE = "Claude restarts and resumes this conversation with the chosen model. Your saved Claude settings stay as they are."
private const val NEXT_LAUNCH_NOTE = "Used by sessions you start next. Default passes nothing, so Claude's own settings decide."
private const val NO_MODEL_YET = "Model"

/** Tab strip control: shows the selected session's live model and effort and opens the switcher; on other tabs it sets the default of the next session. */
@Composable
internal fun ModelControl(state: AppState, project: Project, session: SessionSnapshot?) {
    val target = modelTarget(session, state.modelSwitching)
    val blocked = (target as? ModelTarget.Running)?.blockedReason
    val usage = session?.let { state.sessionUsage[it.ccmSessionId] }
    val label = when {
        state.modelSwitching -> "Switching…"
        target is ModelTarget.Running -> effectiveModelLabel(usage?.modelName(), usage?.effort()) ?: NO_MODEL_YET
        else -> selectionLabel(project.modelSelection())
    }
    val tooltip = blocked ?: if (target is ModelTarget.Running) "Model and effort of this session" else "Model and effort for the next session you start"
    Box {
        AppButton(
            label, { state.modelPickerOpen = !state.modelPickerOpen }, variant = ButtonVariant.SECONDARY, icon = Icons.Cpu,
            enabled = blocked == null, loading = state.modelSwitching, compact = true, tooltip = tooltip,
        )
        if (state.modelPickerOpen && blocked == null) {
            when (target) {
                is ModelTarget.Running -> RunningSwitcher(state, requireNotNull(session), usage?.effort(), label)
                ModelTarget.NextLaunch -> ModelOptions(project.modelSelection(), NEXT_LAUNCH_NOTE, { state.selectModel(project, it) }, { state.modelPickerOpen = false }) {}
            }
        }
    }
}

@Composable
private fun RunningSwitcher(state: AppState, session: SessionSnapshot, liveEffort: String?, liveLabel: String) {
    var draft by remember { mutableStateOf(ModelSelection(null, knownEffort(liveEffort))) }
    val changed = draft.model() != null || draft.effort() != knownEffort(liveEffort)
    ModelOptions(draft, RUNNING_NOTE, { draft = it }, { state.modelPickerOpen = false }, "Running: $liveLabel") {
        AppButton(
            "Restart with ${selectionLabel(draft)}", { restartAfterConfirm(state, session, draft) }, Modifier.fillMaxWidth(),
            variant = ButtonVariant.PRIMARY, enabled = changed,
        )
    }
}

private fun restartAfterConfirm(state: AppState, session: SessionSnapshot, selection: ModelSelection) {
    state.modelPickerOpen = false
    state.confirm = ConfirmRequest(
        title = "Restart session",
        message = "Restart with ${selectionLabel(selection)}? The conversation is resumed in a new process.",
        confirmLabel = "Restart",
        destructive = false,
    ) { state.restartSession(session, selection) }
}

@Composable
private fun ModelOptions(
    selection: ModelSelection,
    note: String,
    onChange: (ModelSelection) -> Unit,
    onDismiss: () -> Unit,
    title: String? = null,
    action: @Composable () -> Unit,
) {
    AnchoredPopup(onDismiss, alignEnd = true, modifier = Modifier.width(pickerWidth)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            title?.let { Label(it, style = Type.heading) }
            Label("Model", color = colors.textSecondary, style = Type.smallMedium)
            ModelOption("Default (from Claude settings)", selection.model() == null) { onChange(ModelSelection(null, selection.effort())) }
            modelPresets.forEach { preset -> PresetOption(preset, selection, onChange) }
            CustomModel(selection, onChange)
            HorizontalRule(Modifier.padding(vertical = Spacing.xs))
            Label("Reasoning effort", color = colors.textSecondary, style = Type.smallMedium)
            SegmentedControl(effortSegments(), selection.effort(), { onChange(ModelSelection(selection.model(), it)) })
            Label(note, color = colors.textTertiary, style = Type.caption, maxLines = 3)
            action()
        }
    }
}

private fun effortSegments(): List<Segment<String?>> =
    listOf(Segment<String?>(null, DEFAULT_EFFORT_LABEL)) + ModelSelection.EFFORT_LEVELS.map { Segment<String?>(it, it.replaceFirstChar(Char::titlecase)) }

@Composable
private fun PresetOption(preset: ModelPreset, selection: ModelSelection, onChange: (ModelSelection) -> Unit) =
    ModelOption(preset.label, selection.model() == preset.value, preset.value) { onChange(ModelSelection(preset.value, selection.effort())) }

@Composable
private fun CustomModel(selection: ModelSelection, onChange: (ModelSelection) -> Unit) {
    val field = remember { TextFieldState(if (isCustomModel(selection)) selection.model() else "") }
    var error by remember { mutableStateOf<String?>(null) }
    val apply = {
        runCatching { ModelSelection(field.text.toString(), selection.effort()) }
            .onSuccess { error = null; onChange(it); field.setTextAndPlaceCursorAtEnd(it.model().orEmpty()) }
            .onFailure { error = it.message }
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(field, Modifier.weight(1f), placeholder = "Custom model id", mono = true, onSubmit = { apply() })
        SecondaryButton("Use", { apply() }, enabled = field.text.isNotBlank())
    }
    error?.let { Label(it, color = colors.danger, style = Type.caption) }
}

@Composable
private fun ModelOption(label: String, selected: Boolean, detail: String? = null, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), base = if (selected) colors.accentSoft else Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.xs + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.width(16.dp)) { if (selected) Glyph(Icons.Check, colors.accent) }
            Label(label, Modifier.weight(1f), style = if (selected) Type.bodyMedium else Type.body)
            detail?.let { Label(it, color = colors.textTertiary, style = Type.monoSmall) }
        }
    }
}
