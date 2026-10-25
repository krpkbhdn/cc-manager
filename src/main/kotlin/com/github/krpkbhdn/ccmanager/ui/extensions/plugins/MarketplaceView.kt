package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.extensions.ListWithDetail
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionAvatar
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ListCard
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val SKELETON_ROWS = 8
private val actionWidth = 132.dp
private val countWidth = 64.dp

/** Searchable catalogue of every added marketplace; the filter runs off the main thread on each change. */
@Composable
fun MarketplaceView(context: ExtensionContext, state: PluginsState, entries: List<InstalledEntry>, modifier: Modifier = Modifier) {
    val search = rememberTextFieldState()
    var marketplace by remember { mutableStateOf<String?>(null) }
    var sort by remember { mutableStateOf(CatalogSort.POPULAR) }
    val selectedId = state.selectedAvailable
    LaunchedEffect(Unit) { state.loadCatalog() }
    val query = CatalogQuery(search.text.toString(), marketplace, sort)
    val results by produceState(emptyList<AvailablePlugin>(), state.catalog, query) { value = withContext(Dispatchers.Default) { filterCatalog(state.catalog, query) } }
    val installedById = remember(entries) { entries.groupBy { it.plugin.id } }
    LaunchedEffect(selectedId, installedById) { selectedId?.let { id -> installedById[id]?.let { state.loadDetails(id, it.first().plugin.projectPath) } } }
    val selected = selectedId?.let { id -> state.catalog.firstOrNull { it.pluginId == id } }
    Column(modifier.fillMaxSize()) {
        Toolbar(state, search, marketplace, { marketplace = it }, sort, { sort = it }, results.size)
        HorizontalRule()
        val detail: (@Composable () -> Unit)? = selected?.let {
            {
                PluginDetailPane(
                    PluginDetailTarget(it.pluginId, it.description),
                    installedById[it.pluginId]?.let { found -> state.details[detailsKey(it.pluginId, found.first().plugin.projectPath)] } ?: DetailsState.Failed("The CLI only describes installed plugins"),
                    { state.selectedAvailable = null },
                    badges = { Glyph(Icons.Download, colors.textTertiary, size = IconSize.medium); Label("${compactCount(it.installCount)} installs", color = colors.textSecondary, style = Type.small) },
                    actions = { InstallButton(context, state, it.pluginId, installedById[it.pluginId].orEmpty()) },
                )
            }
        }
        ListWithDetail(detail, Modifier.weight(1f)) {
            CatalogBody(context, state, results, installedById, selectedId) { state.selectedAvailable = it }
        }
    }
}

@Composable
private fun Toolbar(
    state: PluginsState,
    search: androidx.compose.foundation.text.input.TextFieldState,
    marketplace: String?,
    onMarketplace: (String?) -> Unit,
    sort: CatalogSort,
    onSort: (CatalogSort) -> Unit,
    shown: Int,
) {
    Column(Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            AppTextField(search, Modifier.weight(1f), placeholder = "Search ${compactCount(state.catalog.size.toLong())} plugins by name or description", icon = Icons.Search)
            MarketplaceFilter(state, marketplace, onMarketplace)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            SegmentedControl(CatalogSort.entries.map { Segment(it, it.label) }, sort, onSort)
            Label(if (state.catalog.isEmpty()) "" else if (shown == state.catalog.size) "$shown plugins" else "$shown of ${state.catalog.size}", color = colors.textTertiary, style = Type.small)
        }
    }
}

@Composable
private fun MarketplaceFilter(state: PluginsState, marketplace: String?, onChange: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        AppButton(marketplace ?: "All marketplaces", { open = !open }, icon = Icons.Layers, enabled = state.marketplaces.isNotEmpty())
        if (open) {
            val entries = listOf(MenuEntry("All marketplaces", if (marketplace == null) Icons.Check else null) { onChange(null) }) +
                state.marketplaces.map { MenuEntry(it.name, if (marketplace == it.name) Icons.Check else null) { onChange(it.name) } }
            PopupMenu(entries, { open = false }, alignEnd = true)
        }
    }
}

@Composable
private fun CatalogBody(context: ExtensionContext, state: PluginsState, results: List<AvailablePlugin>, installedById: Map<String, List<InstalledEntry>>, selectedId: String?, onSelect: (String) -> Unit) {
    val failure = state.catalogFailure
    when {
        state.catalog.isEmpty() && state.catalogLoading -> CatalogSkeleton()
        state.catalog.isEmpty() && failure != null -> EmptyState(Icons.CircleAlert, failure.title, failure.message, tint = colors.danger) {
            AppButton("Try again", { state.loadCatalog(force = true) }, icon = Icons.Refresh)
        }
        state.catalog.isEmpty() -> EmptyState(Icons.Layers, "No marketplace added", "Add a marketplace in Sources to see the plugins it offers.")
        results.isEmpty() -> EmptyState(Icons.Search, "No plugins match", "Try fewer words, or pick All marketplaces.")
        else -> {
            val list = rememberLazyListState()
            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    items(results, key = { it.pluginId }) { plugin ->
                        CatalogRow(context, state, plugin, installedById[plugin.pluginId].orEmpty(), selectedId == plugin.pluginId) { onSelect(plugin.pluginId) }
                    }
                }
                ThinScrollbar(list)
            }
        }
    }
}

@Composable
private fun CatalogRow(context: ExtensionContext, state: PluginsState, plugin: AvailablePlugin, entries: List<InstalledEntry>, selected: Boolean, onSelect: () -> Unit) {
    ListCard(selected = selected, onClick = onSelect) {
        ExtensionAvatar(plugin.name)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(plugin.name, style = Type.bodyMedium, tooltipWhenTruncated = true)
                Label(plugin.marketplaceName, color = colors.textTertiary, style = Type.small)
            }
            if (plugin.description.isNotBlank()) Label(plugin.description, color = colors.textSecondary, style = Type.small, maxLines = 2)
        }
        Row(Modifier.width(countWidth), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Glyph(Icons.Download, colors.textTertiary, size = IconSize.medium)
            Label(compactCount(plugin.installCount), Modifier.weight(1f), color = colors.textSecondary, style = Type.numeric, align = TextAlign.End)
        }
        Box(Modifier.width(actionWidth), contentAlignment = Alignment.CenterEnd) { InstallButton(context, state, plugin.pluginId, entries) }
    }
}

@Composable
private fun CatalogSkeleton() {
    Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("Loading the marketplace. The first load can take a minute.", color = colors.textTertiary, style = Type.small, modifier = Modifier.padding(bottom = Spacing.xs))
        repeat(SKELETON_ROWS) { SkeletonBlock(Modifier.fillMaxWidth().height(68.dp), RoundedCornerShape(14.dp)) }
    }
}
