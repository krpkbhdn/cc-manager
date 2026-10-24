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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val railWidth = 200.dp

/** Left rail with Recent, quick places and drives; the place containing the current folder is highlighted. */
@Composable
fun PlacesRail(state: FolderPickerState, onNavigated: () -> Unit, modifier: Modifier = Modifier) {
    val groups = state.places
    Column(
        modifier.width(railWidth).fillMaxHeight().background(colors.sidebar).verticalScroll(rememberScrollState()).padding(Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.xxs),
    ) {
        PlaceSection("Recent", groups.recent, state, onNavigated)
        PlaceSection("Places", groups.places, state, onNavigated)
        PlaceSection("Drives", groups.drives, state, onNavigated)
    }
}

@Composable
private fun PlaceSection(title: String, places: List<Place>, state: FolderPickerState, onNavigated: () -> Unit) {
    if (places.isEmpty()) return
    Label(title.uppercase(), Modifier.padding(start = Spacing.sm, top = Spacing.md, bottom = Spacing.xs), color = colors.textTertiary, style = Type.overline)
    places.forEach { place ->
        val active = place.path == state.current && place.kind != PlaceKind.Recent
        Pressable({ state.open(place.path); onNavigated() }, Modifier.fillMaxWidth(), base = if (active) colors.accentSoft else Color.Transparent) {
            PlaceRow(place, active)
        }
    }
}

@Composable
private fun PlaceRow(place: Place, active: Boolean) {
    Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Glyph(placeIcon(place.kind), if (active) colors.accent else colors.textSecondary)
        Column(Modifier.weight(1f)) {
            Label(place.label, style = if (active) Type.bodyMedium else Type.body)
            place.hint?.let { Label(it, color = colors.textTertiary, style = Type.caption) }
        }
    }
}

private fun placeIcon(kind: PlaceKind): ImageVector = when (kind) {
    PlaceKind.Home -> Icons.Home
    PlaceKind.Desktop -> PickerIcons.Desktop
    PlaceKind.Documents -> PickerIcons.Document
    PlaceKind.Downloads -> PickerIcons.Download
    PlaceKind.Work -> PickerIcons.Briefcase
    PlaceKind.Drive -> PickerIcons.Drive
    PlaceKind.Recent -> Icons.Clock
}
