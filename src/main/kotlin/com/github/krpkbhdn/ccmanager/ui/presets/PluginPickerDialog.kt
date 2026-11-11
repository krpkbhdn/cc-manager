package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.extensions.SectionHeader
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Pick plugins from the added marketplaces and the installed ones; their marketplaces join the preset automatically. */
@Composable
fun PluginPickerDialog(open: ComponentDialog, state: PresetsState) {
    val choices = state.pluginChoices
    val taken = state.find(open.presetId)?.components().orEmpty().filter { it.kind() == PresetComponentKind.PLUGIN }.map { it.name() }.toSet()
    val query = rememberTextFieldState()
    val selected = remember { mutableStateListOf<String>() }
    var error by remember { mutableStateOf<String?>(null) }
    val groups = remember(choices, taken, query.text.toString()) {
        choices?.let { pluginGroups(it.available, it.installed, taken, query.text.toString()) }.orEmpty()
    }
    PickerDialogFrame("Add plugins", Icons.Plug, state::closeDialog) {
        Box(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md)) {
            AppTextField(query, Modifier.fillMaxWidth(), placeholder = "Search plugins", icon = Icons.Search, height = inputHeight)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            when {
                choices == null -> LoadingRow("Loading plugins…")
                groups.isEmpty() -> Label(
                    if (choices.available.isEmpty() && choices.installed.isEmpty()) "No plugins found. Add a marketplace on the Plugins screen first." else "No plugin matches the search",
                    Modifier.padding(Spacing.xl), color = colors.textSecondary,
                )
                else -> PluginRows(groups, selected, { id -> if (id in selected) selected.remove(id) else selected.add(id) }, choices.installed.toSet())
            }
        }
        HorizontalRule()
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            error?.let { Label(it, color = colors.danger, style = Type.small, maxLines = 3) }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(if (selected.isEmpty()) "Nothing selected" else "${selected.size} selected", Modifier.weight(1f), color = colors.textSecondary)
                AppButton("Cancel", state::closeDialog)
                AppButton(
                    if (selected.isEmpty()) "Add" else "Add ${selected.size} ${if (selected.size == 1) "plugin" else "plugins"}",
                    { error = null; state.addPlugins(open.presetId, selected.toList()) { error = it } },
                    variant = ButtonVariant.PRIMARY, enabled = selected.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun LoadingRow(text: String) {
    Row(Modifier.padding(Spacing.xl), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Spinner()
        Label(text, color = colors.textSecondary)
    }
}

@Composable
private fun PluginRows(groups: List<PluginGroup>, selected: List<String>, toggle: (String) -> Unit, installed: Set<String>) {
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(horizontal = Spacing.lg, vertical = Spacing.xs)) {
            groups.forEach { group ->
                item(key = "group-${group.marketplace}") { SectionHeader(group.marketplace, group.plugins.size) }
                items(group.plugins, key = { it.id }) { plugin -> PluginRow(plugin, plugin.id in selected, plugin.id in installed) { toggle(plugin.id) } }
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun PluginRow(plugin: PickerPlugin, checked: Boolean, installed: Boolean, onToggle: () -> Unit) {
    Pressable(onToggle, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.md), Color.Transparent, null, enabled = !plugin.added) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AppCheckbox(checked || plugin.added, { onToggle() }, enabled = !plugin.added)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Label(plugin.name, style = Type.bodyMedium, color = if (plugin.added) colors.textTertiary else colors.textPrimary)
                if (plugin.description.isNotBlank()) Label(plugin.description, color = colors.textTertiary, style = Type.caption)
            }
            val note = if (plugin.added) "Already in the preset" else if (installed) "Installed" else null
            if (note != null) Label(note, color = colors.textTertiary, style = Type.caption)
        }
    }
}
