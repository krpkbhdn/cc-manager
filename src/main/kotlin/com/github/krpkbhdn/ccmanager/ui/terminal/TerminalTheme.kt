package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.graphics.Color
import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.emulator.ColorPalette

/** Colors of a terminal: the scheme follows [dark], the optional overrides let it match the app surface. */
internal data class TerminalTheme(
    val dark: Boolean = true,
    val backgroundOverride: Color? = null,
    val foregroundOverride: Color? = null,
) {
    val background: Color get() = backgroundOverride ?: if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND

    val foreground: Color get() = foregroundOverride ?: if (dark) DARK_FOREGROUND else LIGHT_FOREGROUND

    val selection: Color get() = if (dark) DARK_SELECTION else LIGHT_SELECTION

    private val palette: ColorPalette get() = if (dark) AnsiColorPalette.DARK else AnsiColorPalette.LIGHT

    fun resolve(color: TerminalColor): Color = palette.getForeground(color).toComposeColor()
}
