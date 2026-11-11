package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val offerWidth = 540.dp
private const val NAME_LIMIT = 32

/** "Set up <project>?": the preset to start from, then Review & apply, Not for this project or Later. */
@Composable
fun PresetOfferDialog(offer: PresetOffer, presets: List<ProjectPreset>, state: PresetOfferState) {
    ModalHost(state::later, width = offerWidth) {
        DialogBody("Set up ${offer.project.name.take(NAME_LIMIT)}?", Icons.Sparkles, colors.accent, actions = {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End), verticalAlignment = Alignment.CenterVertically) {
                AppButton("Not for this project", state::decline, variant = ButtonVariant.GHOST)
                AppButton("Later", state::later)
                AppButton("Review & apply", state::reviewAndApply, variant = ButtonVariant.PRIMARY)
            }
        }) {
            Label("This project has no Claude setup from a preset yet. A preset adds instructions, skills, plugins and settings to it. You review every change before anything is written.", color = colors.textSecondary, maxLines = 4)
            PresetDropdown(presets, offer.presetId, state::choose)
        }
    }
}

@Composable
private fun PresetDropdown(presets: List<ProjectPreset>, selectedId: Long, onChoose: (Long) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(Radius.md)
    Box(Modifier.fillMaxWidth()) {
        Pressable({ open = true }, Modifier.fillMaxWidth().border(1.dp, colors.borderStrong, shape), shape, colors.surface, colors.surfaceHover) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("Preset", color = colors.textTertiary, style = Type.small)
                Label(presets.firstOrNull { it.id() == selectedId }?.name().orEmpty(), Modifier.weight(1f), style = Type.bodyMedium, tooltipWhenTruncated = true)
                Glyph(Icons.ChevronsUpDown, colors.textSecondary)
            }
        }
        if (open) PopupMenu(presets.map { preset -> MenuEntry(preset.name(), if (preset.id() == selectedId) Icons.Check else null) { onChoose(preset.id()) } }, { open = false })
    }
}
