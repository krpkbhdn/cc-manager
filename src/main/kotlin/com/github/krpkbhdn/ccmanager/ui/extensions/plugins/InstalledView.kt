package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppSwitch
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionAvatar
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionList
import com.github.krpkbhdn.ccmanager.ui.extensions.listSection
import com.github.krpkbhdn.ccmanager.ui.extensions.ListCard
import com.github.krpkbhdn.ccmanager.ui.extensions.ListSkeleton
import com.github.krpkbhdn.ccmanager.ui.extensions.ListWithDetail
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val INHERITED_HINT = "Managed in your user plugins"

/** Plugins installed in the context with a detail pane for the selected one. */
@Composable
fun InstalledView(context: ExtensionContext, state: PluginsState, entries: List<InstalledEntry>, onBrowse: () -> Unit, modifier: Modifier = Modifier) {
    val selectedKey = state.selectedInstalled
    val selected = entries.firstOrNull { it.plugin.key() == selectedKey }
    LaunchedEffect(selected?.plugin?.key()) { selected?.let { state.loadDetails(it.plugin.id, it.plugin.projectPath) } }
    ListWithDetail(selected?.let { { InstalledDetail(state, it) { state.selectedInstalled = null } } }, modifier) {
        when {
            !state.loaded -> ListSkeleton()
            entries.isEmpty() -> EmptyInstalled(context, onBrowse)
            else -> InstalledList(context, state, entries, selectedKey) { state.selectedInstalled = it }
        }
    }
}

@Composable
private fun InstalledList(context: ExtensionContext, state: PluginsState, entries: List<InstalledEntry>, selectedKey: String?, onSelect: (String) -> Unit) {
    val own = entries.filterNot { it.inherited }
    val row: @Composable (InstalledEntry) -> Unit = { InstalledRow(it, state, selectedKey == it.plugin.key()) { onSelect(it.plugin.key()) } }
    ExtensionList {
        if (context is ExtensionContext.ForProject && own.isEmpty()) item(key = "none") { NothingHere(context) }
        items(own, key = { it.plugin.key() }) { row(it) }
        listSection("inherited", "Inherited from user", entries.filter { it.inherited }, { it.plugin.key() }, row)
    }
}

@Composable
private fun NothingHere(context: ExtensionContext.ForProject) {
    Label("Nothing installed in ${context.name} yet. Plugins you install for the project show up here.", Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs), color = colors.textTertiary, maxLines = 2)
}

@Composable
private fun InstalledRow(entry: InstalledEntry, state: PluginsState, selected: Boolean, onSelect: () -> Unit) {
    val plugin = entry.plugin
    val busy = state.job?.key == plugin.key()
    ListCard(selected = selected, onClick = onSelect) {
        ExtensionAvatar(entry.name, Modifier.alpha(if (plugin.enabled) 1f else Alpha.DISABLED))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(entry.name, color = if (plugin.enabled) colors.textPrimary else colors.textSecondary, style = Type.bodyMedium, tooltipWhenTruncated = true)
                Label(plugin.version, color = colors.textTertiary, style = Type.monoSmall)
            }
            Label(entry.marketplace, color = colors.textTertiary, style = Type.small)
        }
        ScopeBadge(plugin.scope)
        RowActions(entry, state, busy)
    }
}

@Composable
private fun RowActions(entry: InstalledEntry, state: PluginsState, busy: Boolean) {
    var menu by remember { mutableStateOf(false) }
    val plugin = entry.plugin
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        if (busy) Spinner(size = 16.dp)
        WithTooltip(if (entry.inherited) INHERITED_HINT else if (plugin.enabled) "Disable" else "Enable") {
            AppSwitch(plugin.enabled, { state.setEnabled(plugin, it) }, enabled = !entry.inherited && state.job == null)
        }
        Box {
            IconButton(Icons.More, { menu = true }, tooltip = "More", enabled = !entry.inherited && state.job == null)
            if (menu) PopupMenu(
                listOf(
                    MenuEntry("Update", Icons.Refresh) { state.update(plugin) },
                    MenuEntry("Uninstall", Icons.Trash, danger = true, dividerAbove = true) { state.requestUninstall(plugin) },
                ),
                { menu = false },
                alignEnd = true,
            )
        }
    }
}

@Composable
private fun InstalledDetail(state: PluginsState, entry: InstalledEntry, onClose: () -> Unit) {
    val plugin = entry.plugin
    val editable = !entry.inherited && state.job == null
    PluginDetailPane(
        PluginDetailTarget(plugin.id, ""),
        state.details[detailsKey(plugin.id, plugin.projectPath)],
        onClose,
        badges = {
            ScopeBadge(plugin.scope)
            Chip("v${plugin.version}", colors.neutral)
            if (!plugin.enabled) Chip("Disabled", colors.warning)
            if (entry.inherited) Label(INHERITED_HINT, color = colors.textTertiary, style = Type.caption)
        },
        actions = {
            if (!entry.inherited) {
                AppButton(if (plugin.enabled) "Disable" else "Enable", { state.setEnabled(plugin, !plugin.enabled) }, enabled = editable, compact = true)
                AppButton("Update", { state.update(plugin) }, icon = Icons.Refresh, enabled = editable, loading = state.job?.key == plugin.key(), compact = true)
                IconButton(Icons.Trash, { state.requestUninstall(plugin) }, tooltip = "Uninstall", tint = colors.danger, enabled = editable)
            }
        },
    )
}

@Composable
private fun EmptyInstalled(context: ExtensionContext, onBrowse: () -> Unit) {
    val where = if (context is ExtensionContext.ForProject) "in ${context.name}" else "yet"
    EmptyState(Icons.Plug, "No plugins installed $where", "Plugins bundle skills, agents, hooks and MCP servers. Browse the marketplace to add some.") {
        AppButton("Browse marketplace", onBrowse, variant = ButtonVariant.PRIMARY, icon = Icons.Search)
    }
}
