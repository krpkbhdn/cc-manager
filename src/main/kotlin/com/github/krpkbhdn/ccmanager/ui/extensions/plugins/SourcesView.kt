package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.extensions.ListCard
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val SOURCE_HINT = "GitHub owner/repo, a git URL or a local folder"

/** Marketplaces the plugin catalogue comes from: add, refresh and remove. */
@Composable
fun SourcesView(state: PluginsState, modifier: Modifier = Modifier) {
    val source = rememberTextFieldState()
    val add = { if (source.text.isNotBlank() && state.job == null) { state.addMarketplace(source.text.toString()); source.edit { replace(0, length, "") } } }
    val catalogSizes = state.catalog.groupingBy { it.marketplaceName }.eachCount()
    Column(modifier.fillMaxSize()) {
        Row(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppTextField(source, Modifier.weight(1f), placeholder = SOURCE_HINT, icon = Icons.Link, mono = true, onSubmit = add)
            AppButton("Add", add, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, enabled = source.text.isNotBlank() && state.job == null, loading = state.job?.key == ADD_MARKETPLACE_KEY)
            AppButton("Update all", { state.updateMarketplace(null) }, icon = Icons.Refresh, enabled = state.job == null && state.marketplaces.isNotEmpty(), loading = state.job?.key == UPDATE_ALL_KEY)
        }
        HorizontalRule()
        if (state.marketplaces.isEmpty() && state.loaded) {
            EmptyState(Icons.Layers, "No marketplaces yet", "A marketplace is a git repository that lists plugins. Add one above to browse what it offers.")
        } else {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(state.marketplaces, key = { it.name }) { SourceRow(it, catalogSizes[it.name] ?: 0, state) }
                }
                ThinScrollbar(list)
            }
        }
    }
}

@Composable
private fun SourceRow(marketplace: Marketplace, plugins: Int, state: PluginsState) {
    val busy = state.job?.key == marketplaceKey(marketplace.name)
    ListCard {
        Box(Modifier.size(36.dp).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
            Glyph(kindIcon(marketplace.sourceKind), colors.accent, size = IconSize.xlarge)
        }
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(marketplace.name, style = Type.bodyMedium, tooltipWhenTruncated = true)
                Chip(marketplace.sourceKind, colors.neutral)
                if (plugins > 0) Label("$plugins plugins", color = colors.textTertiary, style = Type.small)
            }
            Label(marketplace.reference, color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
        }
        IconButton(Icons.Refresh, { state.updateMarketplace(marketplace.name) }, tooltip = "Update", enabled = state.job == null, active = busy)
        IconButton(Icons.Trash, { state.requestRemoveMarketplace(marketplace) }, tooltip = "Remove", tint = colors.danger, enabled = state.job == null)
    }
}

private fun kindIcon(kind: String): ImageVector = when (kind) {
    "github" -> Icons.Branch
    "path" -> Icons.Folder
    else -> Icons.Globe
}
