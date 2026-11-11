package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.foundation.border
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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.RadioMark
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.presets.summaryOf
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.presets.inputHeight
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Step 1: a searchable list of presets, each marked Applied or Outdated for the project. */
@Composable
fun ApplyChoosePage(state: ApplyPresetState) {
    val list = rememberLazyListState()
    val shown = state.visiblePresets
    Column(Modifier.fillMaxSize()) {
        AppTextField(state.query, Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), placeholder = "Search presets", icon = Icons.Search, height = inputHeight)
        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (shown.isEmpty()) {
                Label("No preset matches the search", Modifier.padding(horizontal = Spacing.xl, vertical = Spacing.md), color = colors.textTertiary, style = Type.small)
            }
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                items(shown, key = { it.id() }) { preset -> PresetChoice(preset, state.statusOf(preset), preset.id() == state.presetId) { state.select(preset.id()) } }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun PresetChoice(preset: ProjectPreset, status: PresetStatus?, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    Pressable(onClick, Modifier.fillMaxWidth().border(1.dp, if (selected) colors.accent else colors.border, shape), shape, if (selected) colors.surfaceSelected else colors.surface, if (selected) null else colors.surfaceHover, role = androidx.compose.ui.semantics.Role.RadioButton) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            RadioMark(selected)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Label(preset.name(), style = Type.bodyMedium, tooltipWhenTruncated = true)
                Label(summaryOf(preset.components()), color = colors.textTertiary, style = Type.caption)
                if (preset.description().isNotBlank()) Label(preset.description(), color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
            }
            status?.let { Chip(it.label, if (it == PresetStatus.OUTDATED) colors.warning else colors.success) }
        }
    }
}
