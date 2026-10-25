package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.extensions.BusyLabel
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionList
import com.github.krpkbhdn.ccmanager.ui.extensions.listSection
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionFailureStrip
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionToolbar
import com.github.krpkbhdn.ccmanager.ui.extensions.ListSkeleton
import com.github.krpkbhdn.ccmanager.ui.extensions.ListWithDetail
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** MCP servers for a [context]; [state] must have been created for the same project. */
@Composable
fun McpPage(context: ExtensionContext, state: McpState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { state.ensureLoaded() }
    val groups = remember(state.servers, state.health, context) { mcpGroups(state.servers, state.health, context) }
    val selected = groups.all.firstOrNull { it.key == state.selected }
    LaunchedEffect(selected?.key) { selected?.let { state.loadDetails(it) } }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            McpToolbar(state, groups.size)
            (state.failure ?: state.loadFailure)?.let { ExtensionFailureStrip(it, onDismiss = { if (state.failure != null) state.failure = null else state.loadFailure = null }) }
            Box(Modifier.weight(1f)) {
                ListWithDetail(selected?.let { { McpDetailPane(it, state) } }) {
                    when {
                        !state.loaded -> ListSkeleton()
                        groups.size == 0 -> EmptyMcp(context, state)
                        else -> McpList(context, state, groups)
                    }
                }
            }
        }
        if (state.adding) AddServerDialog(context, state)
    }
}

@Composable
private fun McpToolbar(state: McpState, count: Int) {
    ExtensionToolbar {
        Label(if (count == 1) "1 server" else "$count servers", color = colors.textSecondary, style = Type.small)
        HealthSummary(state)
        Box(Modifier.weight(1f))
        state.job?.let { BusyLabel(it.label) }
        AppButton("Check health", { state.checkHealth() }, icon = Icons.Activity, enabled = state.healthCheck != HealthCheck.RUNNING && state.loaded, compact = true)
        AppButton("Add server", { state.adding = true }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, enabled = state.job == null, compact = true)
        IconButton(Icons.Refresh, { state.refresh() }, tooltip = "Refresh", enabled = !state.loading && state.job == null)
    }
}

@Composable
private fun HealthSummary(state: McpState) {
    when (state.healthCheck) {
        HealthCheck.RUNNING -> BusyLabel("Checking health")
        HealthCheck.FAILED -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Glyph(Icons.CircleAlert, colors.warning)
            Label("Health check failed: ${state.healthMessage}", color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
        }
        else -> Unit
    }
}

@Composable
private fun McpList(context: ExtensionContext, state: McpState, groups: McpGroups) {
    val row: @Composable (McpEntry) -> Unit = { McpRow(it, state) }
    ExtensionList {
        if (context is ExtensionContext.ForProject && groups.own.isEmpty()) item(key = "none") {
            Label("No servers of its own in ${context.name} yet. Servers you add for the project show up here.", Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs), color = colors.textTertiary, maxLines = 2)
        }
        items(groups.own, key = { it.key }) { row(it) }
        listSection("inherited", "Inherited from user", groups.inherited, { it.key }, row)
        listSection("plugins", "From plugins", groups.plugins, { it.key }, row)
        listSection("connectors", "claude.ai connectors", groups.connectors, { it.key }, row)
    }
}

@Composable
private fun EmptyMcp(context: ExtensionContext, state: McpState) {
    val where = if (context is ExtensionContext.ForProject) "in ${context.name}" else "yet"
    EmptyState(Icons.Server, "No MCP servers $where", "MCP servers give Claude tools and data from other programs, such as a browser, a database or an issue tracker.") {
        AppButton("Add server", { state.adding = true }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
    }
}
