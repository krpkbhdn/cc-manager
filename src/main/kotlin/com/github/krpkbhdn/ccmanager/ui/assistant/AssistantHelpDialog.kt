package com.github.krpkbhdn.ccmanager.ui.assistant

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.Platform
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 640.dp
private val bodyMaxHeight = 480.dp
private val toolNameWidth = 150.dp

/** Mini-reference of the Assistant: the app hotkeys and what its tools can do; both lists are generated, so they stay current. */
@Composable
fun AssistantHelpDialog(tools: List<ToolSummary>, onDismiss: () -> Unit) {
    val hotkeys = helpHotkeys(Platform.isMac)
    val groups = groupTools(tools)
    ModalHost(onDismiss, width = dialogWidth) {
        DialogBody("Assistant help", Icons.Book, colors.accent) {
            Column(
                Modifier.heightIn(max = bodyMaxHeight).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
            ) {
                Heading("Hotkeys")
                hotkeys.chunked((hotkeys.size + 1) / 2).let { (left, right) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.xl)) {
                        HotkeyColumn(left, Modifier.weight(1f))
                        HotkeyColumn(right, Modifier.weight(1f))
                    }
                }
                Heading("What the assistant can do")
                groups.forEach { ToolGroupSection(it) }
                Heading("Try saying")
                assistantExamples.forEach { Label("\"$it\"", color = colors.textSecondary, maxLines = 2) }
            }
        }
    }
}

@Composable
private fun Heading(text: String) = Label(text, style = Type.subtitle)

@Composable
private fun HotkeyColumn(lines: List<HotkeyLine>, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        lines.forEach { line ->
            Row(Modifier.fillMaxWidth()) {
                Label(line.label, Modifier.weight(1f), color = colors.textSecondary)
                Label(line.keys, style = Type.caption, color = colors.textTertiary)
            }
        }
    }
}

@Composable
private fun ToolGroupSection(group: ToolGroup) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(group.title, style = Type.caption, color = colors.textTertiary)
        group.tools.forEach { tool ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Label(tool.name, Modifier.widthIn(min = toolNameWidth), style = Type.monoSmall)
                Label(oneLine(tool.description), Modifier.weight(1f), color = colors.textSecondary, maxLines = 2)
            }
        }
    }
}
