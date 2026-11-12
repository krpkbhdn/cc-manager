package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.CountDot
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.menuSize
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

private val spacingLayers = setOf(SpacingLayer.MARGIN, SpacingLayer.PADDING, SpacingLayer.GAP)
private val chevronSize = 18.dp

/** Number of overlays switched on and reaching the page; the badge on the master button. */
fun overlayCount(t: InspectorToggles): Int = with(t.effective()) { listOf(showAll, showBox, showLabels).count { it } + spacing.size }

fun InspectorToggles.toggleLayer(layer: SpacingLayer) = copy(spacing = if (layer in spacing) spacing - layer else spacing + layer)

/** The master item: everything of margin, padding and gap on, or all of it off when it already is. */
fun InspectorToggles.toggleAllSpacing() = copy(spacing = if (spacing.containsAll(spacingLayers)) spacing - spacingLayers else spacing + spacingLayers)

fun InspectorToggles.withoutOverlays() = copy(showAll = false, showBox = false, showLabels = false, spacing = emptySet())

/** Master "Layers" button plus one split button per overlay group: the icon switches the group, the chevron opens its sub-options. */
@Composable
fun WebOverlayMenu(toggles: InspectorToggles, onChange: (InspectorToggles) -> Unit) {
    val count = overlayCount(toggles)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Box {
            IconButton(Icons.Layers, { onChange(toggles.toggleMaster()) }, tooltip = "Overlays (O)", active = toggles.overlaysOn)
            if (count > 0 && toggles.overlaysOn) CountDot(count, Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp))
        }
        SplitGroup(OverlayGroup.INSPECT, "Inspect", Icons.MousePointer, toggles, onChange)
        SplitGroup(OverlayGroup.SPACING, "Spacing", Icons.Square, toggles, onChange)
        SplitGroup(OverlayGroup.AUDIT, "Audit", Icons.Search, toggles, onChange)
        SplitGroup(OverlayGroup.SIMILAR, "Similar", Icons.Copy, toggles, onChange)
        SplitGroup(OverlayGroup.CONTRAST, "Contrast", Icons.Eye, toggles, onChange)
    }
}

@Composable
private fun SplitGroup(group: OverlayGroup, label: String, icon: ImageVector, t: InspectorToggles, change: (InspectorToggles) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(icon, { change(t.toggleGroup(group)) }, tooltip = "$label (${group.key})", active = t.isOn(group))
        Box {
            IconButton(Icons.ChevronDown, { open = !open }, tooltip = "$label options", size = chevronSize)
            if (open) AnchoredPopup({ open = false }, modifier = Modifier.menuSize()) { MenuColumn(groupEntries(group, t, change), onDismiss = {}) }
        }
    }
}

/** Sub-options of one group as checked entries; the menu stays open while several are toggled. */
internal fun groupEntries(group: OverlayGroup, t: InspectorToggles, change: (InspectorToggles) -> Unit): List<MenuEntry> {
    fun check(label: String, on: Boolean, divider: Boolean = false, flip: () -> InspectorToggles) =
        MenuEntry(label, dividerAbove = divider, checked = on) { change(flip()) }
    fun layer(label: String, l: SpacingLayer) = check(label, l in t.spacing) { t.toggleLayer(l) }
    return when (group) {
        OverlayGroup.INSPECT -> listOf(
            check("Outline all", t.showAll) { t.toggleShowAll() },
            check("Box model", t.showBox) { t.toggleShowBox() },
            check("Labels", t.showLabels) { t.toggleShowLabels() },
        )
        OverlayGroup.SPACING -> listOf(
            check("Show all spacing", t.spacing.containsAll(spacingLayers)) { t.toggleAllSpacing() },
            layer("Margins", SpacingLayer.MARGIN),
            layer("Paddings", SpacingLayer.PADDING),
            layer("Gaps", SpacingLayer.GAP),
            layer("Values", SpacingLayer.VALUES),
            MenuEntry("None", dividerAbove = true) { change(t.copy(spacing = t.spacing - spacingLayers - SpacingLayer.VALUES)) },
        )
        OverlayGroup.AUDIT -> listOf(
            layer("Borders", SpacingLayer.BORDERS),
            layer("Phantom blocks", SpacingLayer.PHANTOMS),
            layer("Misalignment", SpacingLayer.MISALIGN),
        )
        OverlayGroup.SIMILAR -> listOf(check("Highlight similar elements", t.similar) { t.copy(similar = !t.similar) })
        OverlayGroup.CONTRAST -> listOf(check("Contrast check", t.contrast) { t.copy(contrast = !t.contrast) })
    }
}
