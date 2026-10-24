package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.modelLabel
import com.github.krpkbhdn.ccmanager.ui.state.modelPresets
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private const val CUSTOM_ENTRY = "Custom model id…"

/** Model picker built from the shared presets plus a free id; [noneLabel] names what "no model" means for this role. */
@Composable
fun ModelDropdown(model: String?, noneLabel: String, onChange: (String?) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    var custom by remember { mutableStateOf(model != null && modelPresets.none { it.value == model }) }
    val field = remember { TextFieldState(if (custom) model.orEmpty() else "") }
    LaunchedEffect(custom) {
        if (custom) snapshotFlow { field.text.toString() }.collect { onChange(it.ifBlank { null }) }
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Box {
            AppButton(if (custom) CUSTOM_ENTRY else model?.let(::modelLabel) ?: noneLabel, { open = !open }, variant = ButtonVariant.SECONDARY, icon = Icons.Cpu, tooltip = "Model of this role")
            if (open) PopupMenu(entries(noneLabel, onChange) { custom = it }, { open = false })
        }
        if (custom) AppTextField(field, placeholder = "Custom model id", mono = true)
    }
}

private fun entries(noneLabel: String, onChange: (String?) -> Unit, setCustom: (Boolean) -> Unit): List<MenuEntry> =
    listOf(MenuEntry(noneLabel) { setCustom(false); onChange(null) }) +
        modelPresets.map { preset -> MenuEntry(preset.label, hint = preset.value) { setCustom(false); onChange(preset.value) } } +
        MenuEntry(CUSTOM_ENTRY, dividerAbove = true) { setCustom(true) }
