package com.github.krpkbhdn.ccmanager.ui.ide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.SplitActionButton
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.IdeMenuActions
import com.github.krpkbhdn.ccmanager.ui.state.ideMenuEntries
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.InstalledIde

internal val menuWidth = 260.dp

/** The header's IDE split button above the menu body drawn inline, so an offscreen scene can show and click it. */
@Composable
internal fun IdeMenuScene(ides: List<InstalledIde>, default: InstalledIde, icons: Map<String, ImageBitmap>, actions: IdeMenuActions) {
    Column(Modifier.background(colors.background).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        SplitActionButton("Open in IDE", Icons.Code, {}, tooltip = "Open in ${default.name}", leading = { IdeGlyph(icons[default.id]) })
        val entries = ideMenuEntries(ides, default, actions) { ide -> { IdeGlyph(icons[ide.id]) } }
        Column(Modifier.width(menuWidth).background(colors.surfaceRaised, RoundedCornerShape(Radius.md))) { MenuColumn(entries, onDismiss = {}) }
    }
}
