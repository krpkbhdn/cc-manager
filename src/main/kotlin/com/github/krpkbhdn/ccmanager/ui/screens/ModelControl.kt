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
import com.github.krpkbhdn.ccmanager.ui.state.ModelPreset
import com.github.krpkbhdn.ccmanager.ui.state.ModelTarget
import com.github.krpkbhdn.ccmanager.ui.state.effectiveModelLabel
import com.github.krpkbhdn.ccmanager.ui.state.effortCommand
import com.github.krpkbhdn.ccmanager.ui.state.isCustomModel
import com.github.krpkbhdn.ccmanager.ui.state.knownEffort
import com.github.krpkbhdn.ccmanager.ui.state.modelCommand
import com.github.krpkbhdn.ccmanager.ui.state.modelPresets
import com.github.krpkbhdn.ccmanager.ui.state.modelReported
import com.github.krpkbhdn.ccmanager.ui.state.modelTarget
import com.github.krpkbhdn.ccmanager.ui.state.selectionLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.usage.SessionUsage

private val pickerWidth = 420.dp
private const val DEFAULT_EFFORT_LABEL = "Default"
private const val RUNNING_NOTE = "Types /model and /effort into this session. Claude saves the choice as your default, as if you typed it. Clear any half-typed prompt first."
private const val NEXT_LAUNCH_NOTE = "Used by sessions you start next. Default passes nothing, so Claude's own settings decide."
private const val NO_MODEL_YET = "Model"
private const val APPLYING = "Applying…"
private const val CLAUDE_PICKER = "Open Claude's model picker (Alt+P)"

/** Tab strip control: the selected session's live model and effort, changed by typing /model and /effort into it; on other tabs it sets the default of the next session. */
@Composable
internal fun ModelControl(state: AppState, project: Project, session: SessionSnapshot?) {
    val target = modelTarget(session, state.sessions.modelApplying, session != null && state.sessions.isAttached(session.ccmSessionId))
    val blocked = (target as? ModelTarget.Running)?.blockedReason
    val usage = session?.let { state.sessionUsage[it.ccmSessionId] }
    val label = when {
        state.sessions.modelApplying -> APPLYING
        target is ModelTarget.Running -> effectiveModelLabel(usage?.modelName(), usage?.effort()) ?: NO_MODEL_YET
        else -> selectionLabel(project.modelSelection())
    }
    val tooltip = blocked ?: if (target is ModelTarget.Running) "Model and effort of this session" else "Model and effort for the next session you start"
    Box {
        AppButton(
            label, { state.modelPickerOpen = !state.modelPickerOpen }, variant = ButtonVariant.SECONDARY, icon = Icons.Cpu,
            enabled = blocked == null, loading = state.sessions.modelApplying, compact = true, tooltip = tooltip,
        )
        if (state.modelPickerOpen && blocked == null) {
            when (target) {
                is ModelTarget.Running -> RunningSwitcher(state, requireNotNull(session), usage, label)
                ModelTarget.NextLaunch -> NextLaunchDefault(state, project)
            }
        }
    }
}

@Composable
private fun RunningSwitcher(state: AppState, session: SessionSnapshot, usage: SessionUsage?, liveLabel: String) {
    val reportedModel = modelPresets.firstOrNull { modelReported(usage?.modelName(), it.value) }?.value
    fun apply(command: String, reached: (SessionUsage?) -> Boolean) {
        state.modelPickerOpen = false
        state.sessions.applyToSession(session, command, reached)
    }
    ModelOptions(
        selection = ModelSelection(reportedModel, knownEffort(usage?.effort())),
        note = RUNNING_NOTE,
        title = "Running: $liveLabel",
        markDefault = false,
        onModel = { model -> apply(modelCommand(model)) { modelReported(it?.modelName(), model) } },
        onEffort = { effort -> apply(effortCommand(effort)) { effort != null && it?.effort() == effort } },
        onDismiss = { state.modelPickerOpen = false },
    ) {
        AppButton(
            CLAUDE_PICKER, { state.modelPickerOpen = false; state.sessions.openClaudeModelPicker(session) }, Modifier.fillMaxWidth(),
            variant = ButtonVariant.SECONDARY, icon = Icons.Cpu,
        )
    }
}

@Composable
private fun NextLaunchDefault(state: AppState, project: Project) {
    val current = project.modelSelection()
    ModelOptions(
        selection = current,
        note = NEXT_LAUNCH_NOTE,
        title = null,
        markDefault = true,
        onModel = { state.projects.selectModel(project, ModelSelection(it, current.effort())) },
        onEffort = { state.projects.selectModel(project, ModelSelection(current.model(), it)) },
        onDismiss = { state.modelPickerOpen = false },
    ) {}
}

@Composable
private fun ModelOptions(
    selection: ModelSelection,
    note: String,
    title: String?,
    markDefault: Boolean,
    onModel: (String?) -> Unit,
    onEffort: (String?) -> Unit,
    onDismiss: () -> Unit,
    action: @Composable () -> Unit,
) {
    AnchoredPopup(onDismiss, alignEnd = true, modifier = Modifier.width(pickerWidth)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            title?.let { Label(it, style = Type.heading) }
            Label("Model", color = colors.textSecondary, style = Type.smallMedium)
            ModelOption("Default (from Claude settings)", markDefault && selection.model() == null) { onModel(null) }
            modelPresets.forEach { preset -> PresetOption(preset, selection, onModel) }
            CustomModel(selection, onModel)
            HorizontalRule(Modifier.padding(vertical = Spacing.xs))
            Label("Reasoning effort", color = colors.textSecondary, style = Type.smallMedium)
            SegmentedControl(effortSegments(), selection.effort(), onEffort)
            Label(note, color = colors.textTertiary, style = Type.caption, maxLines = 3)
            action()
        }
    }
}

internal fun effortSegments(): List<Segment<String?>> =
    listOf(Segment<String?>(null, DEFAULT_EFFORT_LABEL)) + ModelSelection.EFFORT_LEVELS.map { Segment<String?>(it, it.replaceFirstChar(Char::titlecase)) }

@Composable
private fun PresetOption(preset: ModelPreset, selection: ModelSelection, onModel: (String?) -> Unit) =
    ModelOption(preset.label, selection.model() == preset.value, preset.value) { onModel(preset.value) }

@Composable
private fun CustomModel(selection: ModelSelection, onModel: (String?) -> Unit) {
    val field = remember { TextFieldState(if (isCustomModel(selection)) selection.model() else "") }
    var error by remember { mutableStateOf<String?>(null) }
    val apply = {
        runCatching { ModelSelection(field.text.toString(), null).model() }
            .onSuccess { error = null; field.setTextAndPlaceCursorAtEnd(it.orEmpty()); onModel(it) }
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
