package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import javax.swing.JPanel
import javax.swing.SwingUtilities

/**
 * Embedded terminal of an opened session (see TerminalHost.open). The widget is persistent: leaving composition
 * detaches it from the panel but keeps its scrollback, entering again re-attaches the same instance.
 */
@Composable
fun TerminalView(ccmSessionId: String, modifier: Modifier = Modifier, dark: Boolean = isSystemInDarkTheme()) {
    val terminal = remember(ccmSessionId) { TerminalHost.widgetOf(ccmSessionId) } ?: return
    SwingPanel(
        modifier = modifier,
        factory = { hostPanel(terminal) },
        update = { applyTheme(terminal, dark) },
    )
}

private fun hostPanel(terminal: OpenTerminal): JPanel =
    JPanel(java.awt.BorderLayout()).also {
        it.add(terminal.widget, java.awt.BorderLayout.CENTER)
        SwingUtilities.invokeLater { terminal.widget.terminalPanel.requestFocusInWindow() }
    }

private fun applyTheme(terminal: OpenTerminal, dark: Boolean) {
    if (terminal.settings.dark == dark) return
    terminal.settings.dark = dark
    terminal.widget.terminalPanel.repaint()
}
