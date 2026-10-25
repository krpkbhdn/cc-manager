package com.github.krpkbhdn.ccmanager.ui.run

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.screens.TERMINAL_INSET
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val EMPTY_MESSAGE = "Claude adds run configurations automatically when it works in a worktree. You can also add one yourself."

class RunTabActions(val onRun: (Long) -> Unit, val onStop: (Long) -> Unit, val onAdd: () -> Unit)

/** The selected configuration's status row and console; an explanation with an add button while there is nothing to show. */
@Composable
fun RunTab(entry: RunEntry?, terminal: TerminalPort, actions: RunTabActions, modifier: Modifier = Modifier) {
    val runId = entry?.runId
    if (entry == null || runId == null) {
        EmptyState(Icons.SquareTerminal, if (entry == null) "No run configurations" else entry.name, if (entry == null) EMPTY_MESSAGE else "Not started yet. Run it to see the console here.", modifier) {
            if (entry == null) AppButton("Add configuration", actions.onAdd, icon = Icons.Plus) else AppButton("Run", { actions.onRun(entry.id) }, icon = Icons.Play, enabled = !entry.directoryMissing)
        }
        return
    }
    Column(modifier.fillMaxSize()) {
        StatusRow(entry, actions)
        HorizontalRule()
        Box(Modifier.fillMaxSize().background(colors.terminal).padding(start = TERMINAL_INSET, top = TERMINAL_INSET)) {
            terminal.View(runId, Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun StatusRow(entry: RunEntry, actions: RunTabActions) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label(entry.name, style = Type.bodyMedium)
        Label(entry.directoryHint, Modifier.weight(1f), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
        val (text, tint) = statusOf(entry)
        Chip(text, tint)
        IconButton(if (entry.running) Icons.Refresh else Icons.Play, { actions.onRun(entry.id) }, tooltip = if (entry.running) "Restart" else "Run again", tint = colors.success, enabled = !entry.directoryMissing)
        IconButton(Icons.Stop, { actions.onStop(entry.id) }, tooltip = "Stop", tint = colors.danger, enabled = entry.running)
    }
}

@Composable
private fun statusOf(entry: RunEntry): Pair<String, Color> = when {
    entry.running -> "Running" to colors.success
    entry.exitCode == 0 -> "Exited (0)" to colors.textSecondary
    else -> "Exited (${entry.exitCode})" to colors.danger
}
