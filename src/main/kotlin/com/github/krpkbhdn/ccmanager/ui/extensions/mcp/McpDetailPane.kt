package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.extension.mcp.McpApproval
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.extensions.DetailPaneFrame
import com.github.krpkbhdn.ccmanager.ui.extensions.MonoBlock
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val SECRETS_HINT = "Values of environment variables, headers and URL parameters are hidden until you show them."

/** One server: identity, health, and the raw `claude mcp get` text with secrets hidden unless revealed. */
@Composable
fun McpDetailPane(entry: McpEntry, state: McpState, modifier: Modifier = Modifier) {
    var revealed by remember(entry.key) { mutableStateOf(false) }
    val scroll = rememberScrollState()
    val subtitle = listOfNotNull(entry.transport?.label, entry.target.takeIf { it.isNotBlank() }).joinToString("  ")
    DetailPaneFrame(
        entry.displayName, entry.displayName, subtitle, { state.selected = null }, modifier,
        badges = {
            entry.scope?.let { ScopeBadge(it) } ?: Chip("Plugin ${entry.pluginName}", colors.neutral)
            entry.approval.label()?.let { Chip(it, entry.approval.tint()) }
            HealthMark(state, entry.name)
        },
        actions = {
            if (entry.removable) IconButton(Icons.Trash, { state.requestRemove(entry) }, tooltip = "Remove server", tint = colors.danger, enabled = state.job == null)
        },
    ) {
        Box {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Notes(entry, state)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Label("Configuration", Modifier.weight(1f), style = Type.heading)
                    IconButton(if (revealed) Icons.EyeOff else Icons.Eye, { revealed = !revealed }, tooltip = if (revealed) "Hide values" else "Show values", active = revealed)
                }
                RawConfiguration(state.details[entry.key], revealed)
            }
            ThinScrollbar(scroll)
        }
    }
}

@Composable
private fun Notes(entry: McpEntry, state: McpState) {
    val status = state.statusOf(entry.name)
    if (status != null && status.statusText.isNotBlank() && status.statusText != status.status.label()) Label(status.statusText, color = colors.textSecondary, maxLines = 4)
    when {
        entry.approval == McpApproval.PENDING -> Label("This server is declared in the project's .mcp.json. Claude asks for your approval the first time a session starts in the project.", color = colors.textSecondary, maxLines = 5)
        entry.approval == McpApproval.DISABLED -> Label("You turned this project server off. Claude skips it until you approve it again.", color = colors.textSecondary, maxLines = 4)
        entry.inherited -> Label("Comes from your user configuration. Change it in the user MCP servers.", color = colors.textTertiary, maxLines = 3)
        entry.fromPlugin -> Label("Provided by the ${entry.pluginName} plugin. Disable the plugin to remove it.", color = colors.textTertiary, maxLines = 3)
    }
}

@Composable
private fun RawConfiguration(details: RawDetails?, revealed: Boolean) {
    when (details) {
        null, RawDetails.Loading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(96.dp), RoundedCornerShape(Radius.lg))
        }
        is RawDetails.Failed -> Label("No details available: ${details.message}", color = colors.textTertiary, style = Type.small, maxLines = 4)
        is RawDetails.Loaded -> {
            MonoBlock(if (revealed) details.text.trim() else maskSecrets(details.text.trim()))
            if (!revealed) Label(SECRETS_HINT, color = colors.textTertiary, style = Type.caption, maxLines = 3)
        }
    }
}
