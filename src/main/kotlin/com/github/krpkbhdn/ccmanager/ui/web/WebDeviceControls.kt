package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.menuSize
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val CUSTOM_FIELD_WIDTH = 64.dp
private val CUSTOM_FIELD_HEIGHT = 24.dp
private const val DEFAULT_CUSTOM_WIDTH = 800
private const val DEFAULT_CUSTOM_HEIGHT = 600

fun DeviceSelection.presetLabel(): String = if (responsive) "Responsive" else preset?.name ?: "Custom"

fun DeviceZoom.label(): String = when (this) {
    DeviceZoom.Fit -> "Fit"
    is DeviceZoom.Percent -> "$value%"
}

private fun DeviceSelection.presetEntries(onChange: (DeviceSelection) -> Unit): List<MenuEntry> {
    val groups = DeviceSelection.presetsByCategory.flatMap { (category, presets) ->
        listOf(MenuEntry(category.name.lowercase().replaceFirstChar { it.uppercase() }, enabled = false, dividerAbove = true) {}) +
            presets.map { p ->
                MenuEntry(p.name, hint = "${p.widthCss} x ${p.heightCss}", checked = preset == p) { onChange(DeviceSelection.of(p, this)) }
            }
    }
    return listOf(MenuEntry("Responsive", checked = responsive) { onChange(copy(preset = null, widthCss = 0, heightCss = 0)) }) + groups +
        MenuEntry("Custom", checked = !responsive && preset == null, dividerAbove = true) {
            onChange(if (responsive) DeviceSelection.custom(DEFAULT_CUSTOM_WIDTH, DEFAULT_CUSTOM_HEIGHT, this) else copy(preset = null))
        }
}

private fun DeviceSelection.zoomEntries(onChange: (DeviceSelection) -> Unit): List<MenuEntry> =
    (listOf<DeviceZoom>(DeviceZoom.Fit) + PageViewport.ZOOM_STEPS.map { DeviceZoom.Percent(it) }).map { z ->
        MenuEntry(z.label(), checked = zoom == z) { onChange(copy(zoom = z)) }
    }

/** Compact device-mode row: preset, zoom, rotate and the size readout (editable for a custom size). */
@Composable
fun WebDeviceControls(selection: DeviceSelection, onChange: (DeviceSelection) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        DropdownChip(selection.presetLabel(), selection.presetEntries(onChange))
        selection.scope?.let { Label(it.name.lowercase(), color = colors.textSecondary, style = Type.caption) }
        if (!selection.responsive) {
            DropdownChip(selection.zoom.label(), selection.zoomEntries(onChange))
            IconButton(Icons.Rewind, { onChange(selection.copy(rotated = !selection.rotated)) }, tooltip = "Rotate", active = selection.rotated)
            SizeReadout(selection, onChange)
        }
    }
}

@Composable
private fun SizeReadout(selection: DeviceSelection, onChange: (DeviceSelection) -> Unit) {
    val w = selection.layoutWidth
    val h = selection.layoutHeight
    if (selection.preset != null) {
        Label("$w × $h", color = colors.textSecondary, style = Type.caption)
        return
    }
    val widthField = remember { TextFieldState(w.toString()) }
    val heightField = remember { TextFieldState(h.toString()) }
    fun showSize(size: DeviceSelection) {
        widthField.setTextAndPlaceCursorAtEnd(size.layoutWidth.toString())
        heightField.setTextAndPlaceCursorAtEnd(size.layoutHeight.toString())
    }
    LaunchedEffect(w, h) { showSize(selection) }
    // Always re-shows the result: an invalid or unchanged entry would not recompose and keep the bad text.
    fun commit() {
        val size = selection.withLayoutSize(widthField.text.toString().toIntOrNull(), heightField.text.toString().toIntOrNull())
        showSize(size)
        if (size != selection) onChange(size)
    }
    val commitOnBlur = Modifier.onFocusChanged { if (!it.isFocused) commit() }
    AppTextField(widthField, Modifier.width(CUSTOM_FIELD_WIDTH).then(commitOnBlur), mono = true, height = CUSTOM_FIELD_HEIGHT, onSubmit = ::commit)
    Label("x", color = colors.textSecondary)
    AppTextField(heightField, Modifier.width(CUSTOM_FIELD_WIDTH).then(commitOnBlur), mono = true, height = CUSTOM_FIELD_HEIGHT, onSubmit = ::commit)
}

@Composable
private fun DropdownChip(text: String, entries: List<MenuEntry>) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier.clip(RoundedCornerShape(6.dp)).clickable { open = true }.padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Label(text, style = Type.smallMedium)
            Glyph(Icons.ChevronDown, colors.textSecondary, size = 12.dp)
        }
        if (open) AnchoredPopup({ open = false }, modifier = Modifier.menuSize()) { MenuColumn(entries, onDismiss = { open = false }) }
    }
}
