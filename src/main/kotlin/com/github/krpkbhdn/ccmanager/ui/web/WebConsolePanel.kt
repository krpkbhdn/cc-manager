package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val consoleHeight = 180.dp

/** The page's console: level filter, clear, repeat counters; a line copies itself on click. Page text is shown as plain text only. */
@Composable
fun WebConsolePanel(view: WebView, modifier: Modifier = Modifier) {
    val lines = remember(view.consoleLines, view.consoleFilter) { view.consoleLines.filter { it.level in view.consoleFilter } }
    val list = rememberLazyListState()
    LaunchedEffect(lines.size) { if (lines.isNotEmpty()) list.scrollToItem(lines.lastIndex) }
    Column(modifier.fillMaxWidth().height(consoleHeight)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Label("Console", style = Type.bodyMedium)
            ConsoleLevel.entries.forEach { level ->
                val on = level in view.consoleFilter
                Chip(level.name.lowercase(), if (on) levelTint(level) else colors.textTertiary, Modifier.clickable { view.toggleLevel(level) })
            }
            Box(Modifier.weight(1f))
            IconButton(Icons.Eraser, view::clearConsole, tooltip = "Clear console")
            IconButton(Icons.Close, { view.consoleOpen = false }, tooltip = "Hide console")
        }
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), list) {
                items(lines) { ConsoleRow(it) }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun ConsoleRow(line: ConsoleLine) {
    Row(
        Modifier.fillMaxWidth().clickable { copyToClipboard(line.text) }.padding(horizontal = Spacing.md, vertical = Spacing.xxs),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Label(line.text, Modifier.weight(1f), color = levelTint(line.level), style = Type.monoSmall, maxLines = 3)
        if (line.repeat > 1) CountBadge(line.repeat)
        if (line.source.isNotEmpty()) Label(if (line.line > 0) "${line.source}:${line.line}" else line.source, Modifier.weight(0.5f), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
    }
}

@Composable
private fun levelTint(level: ConsoleLevel): Color = when (level) {
    ConsoleLevel.ERROR -> colors.danger
    ConsoleLevel.WARN -> colors.warning
    ConsoleLevel.INFO -> colors.accent
    ConsoleLevel.LOG -> colors.textPrimary
    ConsoleLevel.DEBUG -> colors.textTertiary
}
