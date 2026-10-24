package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.SectionHeader
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val railWidth = 196.dp

/** Left rail with Recent, quick places and drives; the place containing the current folder is highlighted. */
@Composable
fun PlacesRail(state: FolderPickerState, onNavigated: () -> Unit, modifier: Modifier = Modifier) {
    val groups = state.places
    Column(
        modifier.width(railWidth).fillMaxHeight().background(colors.sidebar).verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        PlaceSection("Recent", groups.recent, state, onNavigated)
        PlaceSection("Places", groups.places, state, onNavigated)
        PlaceSection("Drives", groups.drives, state, onNavigated)
    }
}

@Composable
private fun PlaceSection(title: String, places: List<Place>, state: FolderPickerState, onNavigated: () -> Unit) {
    if (places.isEmpty()) return
    SectionHeader(title, Modifier.padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs))
    places.forEach { place ->
        val active = place.path == state.current && place.kind != PlaceKind.Recent
        Pressable(
            { state.open(place.path); onNavigated() },
            Modifier.fillMaxWidth(),
            base = if (active) colors.surfaceSelected else Color.Transparent,
            hover = if (active) colors.surfaceSelected else colors.surfaceHover,
        ) {
            PlaceRow(place, active)
        }
    }
}

@Composable
private fun PlaceRow(place: Place, active: Boolean) {
    val tint = if (active) colors.accent else colors.textSecondary
    Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        PlaceIcon(place.kind, tint)
        Column(Modifier.weight(1f)) {
            Label(place.label, weight = if (active) FontWeight.SemiBold else FontWeight.Normal)
            place.hint?.let { Label(it, color = colors.textTertiary, size = FontSize.caption) }
        }
    }
}

@Composable
private fun PlaceIcon(kind: PlaceKind, tint: Color) {
    when (kind) {
        PlaceKind.Home -> Glyph(AppIcon.Home, tint)
        PlaceKind.Desktop -> PickerGlyph(PickerIcon.Desktop, tint)
        PlaceKind.Documents -> PickerGlyph(PickerIcon.Document, tint)
        PlaceKind.Downloads -> PickerGlyph(PickerIcon.Download, tint)
        PlaceKind.Work -> PickerGlyph(PickerIcon.Work, tint)
        PlaceKind.Drive -> PickerGlyph(PickerIcon.Drive, tint)
        PlaceKind.Recent -> PickerGlyph(PickerIcon.Clock, tint)
    }
}
