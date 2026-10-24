package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.graphics.Color
import com.jediterm.core.Color as JediColor
import com.jediterm.terminal.emulator.ColorPalette

private const val OPAQUE = 0xFF000000.toInt()

private fun rgb(value: Int) = Color(OPAQUE or value)

internal val DARK_BACKGROUND = rgb(0x0F1115)
internal val DARK_FOREGROUND = rgb(0xD8DADF)
internal val DARK_SELECTION = rgb(0x2F4468)
internal val LIGHT_BACKGROUND = rgb(0xFFFFFF)
internal val LIGHT_FOREGROUND = rgb(0x1F2328)
internal val LIGHT_SELECTION = rgb(0xB6D7FF)

/** ANSI 0-15 in the VS Code dark terminal scheme: red, yellow and bright variants stay clearly distinct. */
private val DARK_ANSI = intArrayOf(
    0x000000, 0xCD3131, 0x0DBC79, 0xE5E510, 0x2472C8, 0xBC3FBC, 0x11A8CD, 0xE5E5E5,
    0x666666, 0xF14C4C, 0x23D18B, 0xF5F43D, 0x3B8EEA, 0xD670D6, 0x29B8DB, 0xFFFFFF,
)

/** ANSI 0-15 in the VS Code light terminal scheme, readable on a white background. */
private val LIGHT_ANSI = intArrayOf(
    0x000000, 0xCD3131, 0x00A000, 0x949800, 0x0451A5, 0xBC05BC, 0x0598BC, 0x555555,
    0x666666, 0xCD3131, 0x14CE14, 0xB5BA00, 0x0451A5, 0xBC05BC, 0x0598BC, 0xA5A5A5,
)

/** Maps the 16 indexed colors; 256-color and truecolor values never reach it, the emulator resolves them to RGB itself. */
internal class AnsiColorPalette(private val rgb: IntArray) : ColorPalette() {
    override fun getForegroundByColorIndex(colorIndex: Int): JediColor = JediColor(rgb[colorIndex])

    override fun getBackgroundByColorIndex(colorIndex: Int): JediColor = JediColor(rgb[colorIndex])

    companion object {
        val DARK = AnsiColorPalette(DARK_ANSI)
        val LIGHT = AnsiColorPalette(LIGHT_ANSI)
    }
}

internal fun JediColor.toComposeColor(): Color = Color(red, green, blue)
