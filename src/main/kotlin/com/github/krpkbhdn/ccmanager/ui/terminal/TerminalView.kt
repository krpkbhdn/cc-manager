package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.jediterm.terminal.ui.JediTermWidget
import java.awt.BorderLayout
import java.awt.KeyboardFocusManager
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.SwingUtilities
import java.awt.Color as AwtColor

private const val VERTICAL_PADDING = 8

/**
 * Embedded terminal of an opened session (see TerminalHost.open). The widget is persistent: leaving composition
 * detaches it from the panel but keeps its scrollback, entering again re-attaches the same instance.
 * [background] and [foreground] override the default colors of the scheme so the terminal can match the app surface.
 */
@Composable
fun TerminalView(
    ccmSessionId: String,
    modifier: Modifier = Modifier,
    dark: Boolean = isSystemInDarkTheme(),
    background: Color? = null,
    foreground: Color? = null,
) {
    val terminal = remember(ccmSessionId) { TerminalHost.widgetOf(ccmSessionId) } ?: return
    SwingPanel(
        modifier = modifier,
        factory = { hostPanel(terminal, dark, background, foreground) },
        update = { applyTheme(terminal, it as JPanel, dark, background, foreground) },
    )
}

private fun hostPanel(terminal: OpenTerminal, dark: Boolean, background: Color?, foreground: Color?): JPanel =
    JPanel(BorderLayout()).also {
        it.border = BorderFactory.createEmptyBorder(VERTICAL_PADDING, 0, VERTICAL_PADDING, 0)
        it.add(terminal.widget, BorderLayout.CENTER)
        applyTheme(terminal, it, dark, background, foreground)
        SwingUtilities.invokeLater { terminal.widget.terminalPanel.requestFocusInWindow() }
    }

private fun applyTheme(terminal: OpenTerminal, host: JPanel, dark: Boolean, background: Color?, foreground: Color?) {
    val settings = terminal.settings
    val newBackground = background?.let { AwtColor(it.toArgb()) }
    val newForeground = foreground?.let { AwtColor(it.toArgb()) }
    val changed = settings.dark != dark || settings.backgroundOverride != newBackground ||
        settings.foregroundOverride != newForeground
    settings.dark = dark
    settings.backgroundOverride = newBackground
    settings.foregroundOverride = newForeground
    host.background = settings.background
    terminal.widget.background = settings.background
    if (changed) terminal.widget.terminalPanel.repaint()
}

/** True while the keyboard focus sits inside any session terminal. */
fun terminalHasFocus(): Boolean =
    SwingUtilities.getAncestorOfClass(JediTermWidget::class.java, KeyboardFocusManager.getCurrentKeyboardFocusManager().focusOwner) != null
