package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.terminal.TerminalColor
import com.jediterm.terminal.emulator.ColorPalette
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AnsiColorPaletteTest {

    private fun rgb(color: com.jediterm.core.Color) = (color.red shl 16) or (color.green shl 8) or color.blue

    @Test
    fun redAndYellowAreDistinctInBothSchemes() {
        for (palette in listOf(AnsiColorPalette.DARK, AnsiColorPalette.LIGHT)) {
            val red = rgb(palette.getForeground(TerminalColor.index(1)))
            val yellow = rgb(palette.getForeground(TerminalColor.index(3)))
            val brightRed = rgb(palette.getForeground(TerminalColor.index(9)))
            assertThat(red).isNotEqualTo(yellow)
            assertThat(brightRed).isNotEqualTo(yellow)
        }
    }

    @Test
    fun ansiIndexesResolveToTheSchemeValues() {
        assertThat(rgb(AnsiColorPalette.DARK.getForeground(TerminalColor.index(1)))).isEqualTo(0xCD3131)
        assertThat(rgb(AnsiColorPalette.DARK.getBackground(TerminalColor.index(15)))).isEqualTo(0xFFFFFF)
        assertThat(rgb(AnsiColorPalette.LIGHT.getForeground(TerminalColor.index(4)))).isEqualTo(0x0451A5)
    }

    @Test
    fun the256ColorCubeAndGrayRampMatchXterm() {
        fun indexed(index: Int) = rgb(ColorPalette.getIndexedTerminalColor(index)!!.toColor())
        assertThat(indexed(208)).isEqualTo(0xFF8700)
        assertThat(indexed(209)).isEqualTo(0xFF875F)
        assertThat(indexed(214)).isEqualTo(0xFFAF00)
        assertThat(indexed(216)).isEqualTo(0xFFAF87)
        assertThat(indexed(16)).isEqualTo(0x000000)
        assertThat(indexed(231)).isEqualTo(0xFFFFFF)
        assertThat(indexed(232)).isEqualTo(0x080808)
        assertThat(indexed(255)).isEqualTo(0xEEEEEE)
    }

    @Test
    fun truecolorIsNeverRemapped() {
        val claudeOrange = TerminalColor.rgb(215, 119, 87)
        assertThat(rgb(AnsiColorPalette.DARK.getForeground(claudeOrange))).isEqualTo(0xD77757)
        assertThat(rgb(AnsiColorPalette.LIGHT.getBackground(claudeOrange))).isEqualTo(0xD77757)
    }

    @Test
    fun overridesReplaceSchemeDefaultColors() {
        val settings = TerminalSettings(dark = true)
        assertThat(settings.background).isEqualTo(DARK_BACKGROUND)
        settings.backgroundOverride = java.awt.Color(0x101820)
        settings.dark = false
        assertThat(settings.background).isEqualTo(java.awt.Color(0x101820))
        assertThat(settings.foreground).isEqualTo(LIGHT_FOREGROUND)
        assertThat(rgb(settings.defaultBackground.toColor())).isEqualTo(0x101820)
    }
}
