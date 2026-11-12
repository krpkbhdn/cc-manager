package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.CountDot
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.menuSize
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.icons.Icons

private val spacingLayers = setOf(SpacingLayer.MARGIN, SpacingLayer.PADDING, SpacingLayer.GAP)

/** Number of overlays switched on; the badge on the menu button. */
fun overlayCount(t: InspectorToggles): Int = listOf(t.showAll, t.showBox, t.showLabels).count { it } + t.spacing.size

fun InspectorToggles.toggleLayer(layer: SpacingLayer) = copy(spacing = if (layer in spacing) spacing - layer else spacing + layer)

/** The master item: everything of margin, padding and gap on, or all of it off when it already is. */
fun InspectorToggles.toggleAllSpacing() = copy(spacing = if (spacing.containsAll(spacingLayers)) spacing - spacingLayers else spacing + spacingLayers)

fun InspectorToggles.withoutOverlays() = copy(showAll = false, showBox = false, showLabels = false, spacing = emptySet())

/** Overlay switches as a dropdown button with a count badge; the menu stays open while several are toggled. */
@Composable
fun WebOverlayMenu(toggles: InspectorToggles, onChange: (InspectorToggles) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val count = overlayCount(toggles)
    Box {
        IconButton(Icons.Layers, { open = !open }, tooltip = "Overlays", active = count > 0)
        if (count > 0) CountDot(count, Modifier.align(Alignment.TopEnd).padding(top = 1.dp, end = 1.dp))
        if (open) AnchoredPopup({ open = false }, modifier = Modifier.menuSize()) { MenuColumn(overlayEntries(toggles, onChange), onDismiss = {}) }
    }
}

private fun overlayEntries(t: InspectorToggles, change: (InspectorToggles) -> Unit): List<MenuEntry> {
    fun check(label: String, on: Boolean, divider: Boolean = false, hint: String? = null, flip: () -> InspectorToggles) =
        MenuEntry(label, hint = hint, dividerAbove = divider, checked = on) { change(flip()) }
    fun layer(label: String, l: SpacingLayer, divider: Boolean = false) = check(label, l in t.spacing, divider) { t.toggleLayer(l) }
    fun header(label: String) = MenuEntry(label, enabled = false, dividerAbove = true) {}
    return listOf(
        check("Outline all", t.showAll) { t.toggleShowAll() },
        check("Box model", t.showBox) { t.toggleShowBox() },
        check("Labels", t.showLabels) { t.toggleShowLabels() },
        header("Spacing"),
        check("Show all spacing", t.spacing.containsAll(spacingLayers)) { t.toggleAllSpacing() },
        layer("Margins", SpacingLayer.MARGIN),
        layer("Paddings", SpacingLayer.PADDING),
        layer("Gaps", SpacingLayer.GAP),
        layer("Values", SpacingLayer.VALUES),
        header("Audit"),
        layer("Borders", SpacingLayer.BORDERS),
        layer("Phantom blocks", SpacingLayer.PHANTOMS),
        layer("Misalignment", SpacingLayer.MISALIGN),
        MenuEntry("None", dividerAbove = true) { change(t.withoutOverlays()) },
    )
}
