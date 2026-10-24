package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Shortcut
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.shortcuts.Platform
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val sheetWidth = 460.dp
private const val PROJECT_RANGE_LABEL = "Project 1-9"
private const val TERMINAL_NOTE = "Plain Ctrl+K and Ctrl+N work outside the terminal; inside it they stay with Claude."

/** Cheat-sheet of every [AppShortcut]; opens with the shortcut-sheet chord or F1. */
@Composable
fun ShortcutSheet(onDismiss: () -> Unit) {
    ModalHost(onDismiss, width = sheetWidth) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Label("Keyboard shortcuts", style = Type.subtitle)
            HorizontalRule()
            sheetEntries().forEach { (label, shortcut) -> SheetRow(label, shortcut) }
            if (!Platform.isMac) Label(TERMINAL_NOTE, color = colors.textTertiary, style = Type.caption, maxLines = 2)
        }
    }
}

private fun sheetEntries(): List<Pair<String, AppShortcut>> =
    AppShortcut.entries
        .filter { it != AppShortcut.CLOSE_OVERLAY && (it.projectSlot ?: 1) == 1 }
        .map { (if (it.projectSlot != null) PROJECT_RANGE_LABEL else it.label) to it }

@Composable
private fun SheetRow(label: String, shortcut: AppShortcut) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(label, Modifier.weight(1f), color = colors.textPrimary)
        Shortcut(*rowKeys(shortcut).toTypedArray())
    }
}

private fun rowKeys(shortcut: AppShortcut): List<String> =
    if (shortcut.projectSlot != null) shortcut.keys().dropLast(1) + "1-9" else shortcut.keys()
