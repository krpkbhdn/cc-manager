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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Search, New preset, the lazy list of presets and the restore link; the list is as long as the user's presets. */
@Composable
fun PresetList(state: PresetsState, modifier: Modifier = Modifier) {
    val list = rememberLazyListState()
    val shown = state.visible
    Column(modifier) {
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppTextField(state.query, Modifier.fillMaxWidth(), placeholder = "Search presets", icon = Icons.Search, height = inputHeight)
            AppButton("New preset", state::newPreset, Modifier.fillMaxWidth(), icon = Icons.Plus)
        }
        Box(Modifier.weight(1f)) {
            if (shown.isEmpty()) {
                Label("No preset matches the search", Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), color = colors.textTertiary, style = Type.small)
            }
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                items(shown, key = { it.id() }) { preset -> PresetRow(preset, preset.id() == state.selected?.id()) { state.select(preset) } }
            }
            ThinScrollbar(list)
        }
        HorizontalRule()
        Row(Modifier.fillMaxWidth().padding(Spacing.sm)) {
            AppButton("Restore bundled presets", state::requestRestoreBundled, variant = ButtonVariant.GHOST, icon = Icons.Undo, compact = true)
        }
    }
}

@Composable
private fun PresetRow(preset: ProjectPreset, selected: Boolean, onClick: () -> Unit) {
    val chip = chipOf(preset)
    Pressable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.md), if (selected) colors.surfaceSelected else Color.Transparent, if (selected) colors.surfaceSelected else colors.surfaceHover) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Label(preset.name(), style = Type.bodyMedium, tooltipWhenTruncated = true)
                Label(summaryOf(preset.components()), color = colors.textTertiary, style = Type.caption)
            }
            if (chip != null) Chip(chip, if (preset.defaultChanged()) colors.warning else colors.neutral)
        }
    }
}
