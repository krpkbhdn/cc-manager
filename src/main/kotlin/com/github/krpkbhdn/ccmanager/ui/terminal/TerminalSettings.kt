package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.emulator.ColorPalette
import com.jediterm.terminal.ui.settings.DefaultSettingsProvider
import java.awt.Font
import java.awt.Color as AwtColor

private const val JETBRAINS_MONO_ADVANCE = 0.6f

/** JetBrains Mono advances 0.6 em, so sizing by whole-pixel cell width keeps glyphs exactly one cell wide and block characters seamless. */
internal fun fontSizeForCellWidth(pixels: Int): Float = pixels / JETBRAINS_MONO_ADVANCE

internal val DEFAULT_FONT_SIZE = fontSizeForCellWidth(8)
internal val MIN_FONT_SIZE = fontSizeForCellWidth(6)
internal val MAX_FONT_SIZE = fontSizeForCellWidth(20)
private const val SCROLLBACK_LINES = 10_000

private fun jediColor(color: AwtColor) = com.jediterm.core.Color(color.rgb and 0xFFFFFF)

/**
 * JediTerm look: colors follow [dark] and the optional overrides (switchable at runtime), per-glyph font fallback,
 * no blinking, long scrollback, no bell. Shift+Enter sends ESC CR, which Claude reads as a newline.
 */
internal class TerminalSettings(dark: Boolean) : DefaultSettingsProvider() {
    @Volatile
    var dark: Boolean = dark

    @Volatile
    var backgroundOverride: AwtColor? = null

    @Volatile
    var foregroundOverride: AwtColor? = null

    @Volatile
    var fontSize: Float = DEFAULT_FONT_SIZE
        set(value) {
            field = value.coerceIn(MIN_FONT_SIZE, MAX_FONT_SIZE)
            fonts = GlyphFontChain(field)
        }

    @Volatile
    var fonts: GlyphFontChain = GlyphFontChain(DEFAULT_FONT_SIZE)
        private set

    val background: AwtColor get() = backgroundOverride ?: if (dark) DARK_BACKGROUND else LIGHT_BACKGROUND

    val foreground: AwtColor get() = foregroundOverride ?: if (dark) DARK_FOREGROUND else LIGHT_FOREGROUND

    private val dynamicForeground = TerminalColor { jediColor(foreground) }
    private val dynamicBackground = TerminalColor { jediColor(background) }

    override fun getDefaultStyle(): TextStyle = TextStyle(dynamicForeground, dynamicBackground)

    override fun getDefaultForeground(): TerminalColor = dynamicForeground

    override fun getDefaultBackground(): TerminalColor = dynamicBackground

    override fun getSelectionColor(): TextStyle = TextStyle(
        defaultForeground,
        TerminalColor { jediColor(if (dark) DARK_SELECTION else LIGHT_SELECTION) },
    )

    override fun useInverseSelectionColor(): Boolean = false

    override fun getTerminalColorPalette(): ColorPalette = if (dark) AnsiColorPalette.DARK else AnsiColorPalette.LIGHT

    override fun getTerminalFont(): Font = fonts.regular

    override fun getTerminalFontSize(): Float = fontSize

    override fun getBufferMaxLinesCount(): Int = SCROLLBACK_LINES

    override fun caretBlinkingMs(): Int = 0

    override fun audibleBell(): Boolean = false

    override fun copyOnSelect(): Boolean = false

    override fun shiftEnterSendsEscCR(): Boolean = true

    override fun DECCompatibilityMode(): Boolean = false
}
