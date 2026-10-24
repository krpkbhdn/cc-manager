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
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ModelPreset
import com.github.krpkbhdn.ccmanager.ui.state.isCustomModel
import com.github.krpkbhdn.ccmanager.ui.state.modelPresets
import com.github.krpkbhdn.ccmanager.ui.state.selectionLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val pickerWidth = 420.dp
private const val DEFAULT_EFFORT_LABEL = "Default"
private const val PICKER_NOTE = "Applies to sessions started from now on. Default passes nothing, so Claude's own settings decide. A level the model lacks falls back to the highest it supports."

/** Button in the project header that opens the model and effort choice for the project's next sessions. */
@Composable
internal fun ModelPicker(state: AppState, project: Project) {
    val selection = project.modelSelection()
    Box {
        SecondaryButton(selectionLabel(selection), { state.modelPickerOpen = !state.modelPickerOpen }, icon = Icons.Cpu, tooltip = "Model and reasoning effort for new sessions")
        if (state.modelPickerOpen) {
            ModelPickerPanel(selection, { state.selectModel(project, it) }) { state.modelPickerOpen = false }
        }
    }
}

@Composable
private fun ModelPickerPanel(selection: ModelSelection, onSelect: (ModelSelection) -> Unit, onDismiss: () -> Unit) {
    AnchoredPopup(onDismiss, alignEnd = true, modifier = Modifier.width(pickerWidth)) {
        Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label("Model", color = colors.textSecondary, style = Type.smallMedium)
            ModelOption("Default (from Claude settings)", selection.model() == null) { onSelect(ModelSelection(null, selection.effort())) }
            modelPresets.forEach { preset -> PresetOption(preset, selection, onSelect) }
            CustomModel(selection, onSelect)
            HorizontalRule(Modifier.padding(vertical = Spacing.xs))
            Label("Reasoning effort", color = colors.textSecondary, style = Type.smallMedium)
            SegmentedControl(effortSegments(), selection.effort(), { onSelect(ModelSelection(selection.model(), it)) })
            Label(PICKER_NOTE, color = colors.textTertiary, style = Type.caption, maxLines = 4)
        }
    }
}

private fun effortSegments(): List<Segment<String?>> =
    listOf(Segment<String?>(null, DEFAULT_EFFORT_LABEL)) + ModelSelection.EFFORT_LEVELS.map { Segment<String?>(it, it.replaceFirstChar(Char::titlecase)) }

@Composable
private fun PresetOption(preset: ModelPreset, selection: ModelSelection, onSelect: (ModelSelection) -> Unit) =
    ModelOption(preset.label, selection.model() == preset.value, preset.value) { onSelect(ModelSelection(preset.value, selection.effort())) }

@Composable
private fun CustomModel(selection: ModelSelection, onSelect: (ModelSelection) -> Unit) {
    val field = remember { TextFieldState(if (isCustomModel(selection)) selection.model() else "") }
    var error by remember { mutableStateOf<String?>(null) }
    val apply = {
        runCatching { ModelSelection(field.text.toString(), selection.effort()) }
            .onSuccess { error = null; onSelect(it); field.setTextAndPlaceCursorAtEnd(it.model().orEmpty()) }
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
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), base = if (selected) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.md - 2.dp, vertical = Spacing.xs + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.width(16.dp)) { if (selected) Glyph(Icons.Check, colors.accent) }
            Label(label, Modifier.weight(1f), style = if (selected) Type.bodyMedium else Type.body)
            detail?.let { Label(it, color = colors.textTertiary, style = Type.monoSmall) }
        }
    }
}
