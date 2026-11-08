package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.state.compactTokens
import com.github.krpkbhdn.ccmanager.ui.state.mcpServerTokens
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown.Item

private const val APPROXIMATE_MARK = "~"

/** Every item of every category claude `/context` printed, heaviest first; a leading "~" marks an approximate value. */
@Composable
internal fun BreakdownDetails(breakdown: ContextBreakdown) {
    Column(verticalArrangement = spacedBy(Spacing.lg)) {
        ItemList("Memory files", breakdown.memoryFiles())
        McpServers(breakdown)
        ItemList("Custom agents", breakdown.agents())
        ItemList("Skills", breakdown.skills())
        breakdown.otherSections().forEach { ItemList(it.title(), it.items()) }
        if (breakdown.memoryFiles().isEmpty() && breakdown.mcpTools().isEmpty() && breakdown.agents().isEmpty() && breakdown.skills().isEmpty() && breakdown.otherSections().isEmpty()) {
            Label("Claude's /context output has no extra items; headless mode ignores \"all\".", color = colors.textSecondary, style = Type.body, maxLines = WRAP)
        }
    }
}

@Composable
private fun McpServers(breakdown: ContextBreakdown) {
    val toolsByServer = breakdown.mcpTools().groupBy { it.group() }
    val servers = mcpServerTokens(breakdown)
    if (servers.isEmpty()) return
    Section("MCP servers", servers.sumOf { it.second }) {
        servers.forEach { (server, tokens) ->
            Row(horizontalArrangement = spacedBy(Spacing.sm)) {
                Label(server, Modifier.weight(1f), style = Type.bodyMedium, maxLines = WRAP)
                Label("${toolsByServer.getValue(server).size} tools", color = colors.textTertiary, style = Type.caption)
                Label(compactTokens(tokens), Modifier.width(tokensWidth), color = colors.textSecondary, style = Type.numeric)
            }
            Column(Modifier.padding(start = Spacing.lg), verticalArrangement = spacedBy(Spacing.xs)) {
                toolsByServer.getValue(server).sortedByDescending { it.tokens() }.forEach { DetailRow(it.name().substringAfter("__${server}__", it.name()), null, it) }
            }
        }
    }
}

@Composable
private fun ItemList(title: String, items: List<Item>) {
    if (items.isEmpty()) return
    Section(title, items.sumOf { it.tokens() }) { items.sortedByDescending { it.tokens() }.forEach { DetailRow(it.name(), it.group().takeIf { group -> group.isNotBlank() }, it) } }
}

@Composable
private fun Section(title: String, tokens: Long, content: @Composable () -> Unit) {
    Column(verticalArrangement = spacedBy(Spacing.xs)) {
        Row(horizontalArrangement = spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Label(title, Modifier.weight(1f), style = Type.bodyMedium, maxLines = WRAP)
            Label(compactTokens(tokens), Modifier.width(tokensWidth), color = colors.textSecondary, style = Type.numeric)
        }
        content()
    }
}

@Composable
private fun DetailRow(name: String, group: String?, item: Item) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = spacedBy(Spacing.sm)) {
        Label(name, Modifier.weight(1f), color = colors.textSecondary, style = Type.caption, maxLines = WRAP)
        group?.let { Label(it, Modifier.weight(0.4f), color = colors.textTertiary, style = Type.caption, maxLines = WRAP) }
        Label((if (item.approximate()) APPROXIMATE_MARK else "") + compactTokens(item.tokens()), Modifier.width(tokensWidth), color = colors.textSecondary, style = Type.numericCaption)
    }
}
