package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.emulator.ColorPaletteImpl
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Font
import java.awt.GraphicsEnvironment
import java.awt.Color as AwtColor

private const val FONT_SIZE = 13f
private const val SCROLLBACK_LINES = 10_000
private const val FALLBACK_FONT = Font.MONOSPACED

private val DARK_BACKGROUND = AwtColor(0x1E1F22)
private val DARK_FOREGROUND = AwtColor(0xDFE1E5)
private val LIGHT_BACKGROUND = AwtColor(0xFFFFFF)
private val LIGHT_FOREGROUND = AwtColor(0x080808)
private val DARK_SELECTION = AwtColor(0x2E436E)
private val LIGHT_SELECTION = AwtColor(0xA6D2FF)

private val PREFERRED_FONTS = listOf(
    "JetBrains Mono", "Cascadia Mono", "Consolas", "Menlo", "DejaVu Sans Mono", "Liberation Mono",
)

internal fun pickFontFamily(installed: Set<String>, preferred: List<String> = PREFERRED_FONTS): String =
    preferred.firstOrNull { it in installed } ?: FALLBACK_FONT

private val fontFamily: String by lazy {
    pickFontFamily(GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet())
}

private fun terminalColor(color: AwtColor) = TerminalColor.rgb(color.red, color.green, color.blue)

/** JediTerm look: colors follow [dark] (switchable at runtime), no blinking, long scrollback, no bell. */
internal class TerminalSettings(dark: Boolean) : DefaultSettingsProvider() {
    @Volatile
    var dark: Boolean = dark

    override fun getDefaultForeground(): TerminalColor =
        terminalColor(if (dark) DARK_FOREGROUND else LIGHT_FOREGROUND)

    override fun getDefaultBackground(): TerminalColor =
        terminalColor(if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND)

    override fun getSelectionColor(): TextStyle = TextStyle(
        defaultForeground,
        terminalColor(if (dark) DARK_SELECTION else LIGHT_SELECTION),
    )

    override fun getTerminalColorPalette(): ColorPalette = ColorPaletteImpl.XTERM_PALETTE

    override fun getTerminalFont(): Font = Font(fontFamily, Font.PLAIN, terminalFontSize.toInt())

    override fun getTerminalFontSize(): Float = FONT_SIZE

    override fun getBufferMaxLinesCount(): Int = SCROLLBACK_LINES

    override fun caretBlinkingMs(): Int = 0

    override fun audibleBell(): Boolean = false

    override fun copyOnSelect(): Boolean = false
}
