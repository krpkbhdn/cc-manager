package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val tabPadding = 9.dp
private const val DESCRIPTION_LINES = 3
private const val DESCRIPTION_MAX_LINES = 5

/** One preset: name and description edited in place, actions, a count line and the tab of its components. */
@Composable
fun PresetPane(state: PresetsState, preset: ProjectPreset, onApply: (Long) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier) {
        PresetHeader(state, preset, onApply)
        PresetTabs(state, preset)
        HorizontalRule()
        ComponentList(state, preset, state.tab, Modifier.weight(1f).fillMaxSize())
    }
}

@Composable
private fun PresetHeader(state: PresetsState, preset: ProjectPreset, onApply: (Long) -> Unit) {
    val name = remember(preset.id(), preset.name()) { TextFieldState(preset.name()) }
    val description = remember(preset.id(), preset.description()) { TextFieldState(preset.description()) }
    val save = {
        val newName = name.text.toString().trim()
        val newDescription = description.text.toString().trim()
        if (newName.isEmpty()) {
            name.edit { replace(0, length, preset.name()) }
        } else if (newName != preset.name() || newDescription != preset.description()) {
            state.saveDetails(preset, newName, newDescription)
        }
    }
    Column(Modifier.fillMaxWidth().padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextField(name, Modifier.fillMaxWidth().onFocusChanged { if (!it.hasFocus) save() }, placeholder = "Preset name", onSubmit = save, height = inputHeight)
        AppTextArea(description, Modifier.fillMaxWidth().onFocusChanged { if (!it.hasFocus) save() }, placeholder = "What this setup is for", minLines = DESCRIPTION_LINES, maxLines = DESCRIPTION_MAX_LINES)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AppButton("Apply to project…", { onApply(preset.id()) }, variant = ButtonVariant.PRIMARY, icon = Icons.Play)
            Spacer(Modifier.weight(1f))
            if (preset.bundledKey() != null && preset.edited()) IconButton(Icons.Undo, { state.requestRestoreDefault(preset) }, tooltip = "Restore the shipped version")
            IconButton(Icons.Copy, { state.duplicate(preset) }, tooltip = "Duplicate preset")
            IconButton(Icons.Trash, { state.requestDelete(preset) }, tooltip = "Delete preset", tint = colors.danger)
        }
    }
}

@Composable
private fun PresetTabs(state: PresetsState, preset: ProjectPreset) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.md).padding(bottom = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        PresetTab.entries.forEach { tab -> TabPill(tab, tab.countIn(preset), state.tab == tab) { state.tab = tab } }
    }
}

@Composable
internal fun TabPill(tab: PresetTab, count: Int, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick, shape = RoundedCornerShape(Radius.md), base = if (selected) colors.accentSoft else Color.Transparent, hover = null) {
        Row(Modifier.padding(horizontal = tabPadding, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(0.dp)) {
            Label(tab.label, color = if (selected) colors.accent else colors.textSecondary, style = if (selected) Type.smallMedium else Type.small)
            if (count > 0) Label(count.toString(), Modifier.padding(start = Spacing.xxs), color = colors.textTertiary, style = Type.caption)
        }
    }
}
