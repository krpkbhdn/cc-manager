package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val listWidth = 240.dp

/**
 * Settings page "Project presets": the preset list on the left, the selected preset's components on the right.
 * [onApply] gets the id of the preset the user wants to apply to a project.
 */
@Composable
fun PresetsPage(state: PresetsState, modifier: Modifier = Modifier, onApply: (presetId: Long) -> Unit = {}) {
    Row(modifier.fillMaxSize()) {
        PresetList(state, Modifier.width(listWidth).fillMaxHeight().background(colors.sidebar))
        VerticalRule()
        val preset = state.selected
        if (preset == null) {
            EmptyState(Icons.Tasks, "No presets yet", "A preset is a complete setup for a project: plugins, skills, instructions, files and settings.", Modifier.weight(1f)) {
                AppButton("New preset", state::newPreset, icon = Icons.Plus)
            }
        } else {
            PresetPane(state, preset, onApply, Modifier.weight(1f).fillMaxHeight())
        }
    }
}
