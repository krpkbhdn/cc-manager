package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.Font

class GlyphFontChainTest {
    private val bundledOnly = GlyphFontChain(DEFAULT_FONT_SIZE, installedFamilies = emptySet())

    @Test
    fun firstDisplayingPicksTheFirstCandidateThatCanDrawTheGlyph() {
        val canDisplay = { face: String, cp: Int -> (face == "a" && cp == 1) || (face == "b" && cp <= 2) }
        assertThat(firstDisplaying(listOf("a", "b"), 1, canDisplay, "fallback")).isEqualTo("a")
        assertThat(firstDisplaying(listOf("a", "b"), 2, canDisplay, "fallback")).isEqualTo("b")
        assertThat(firstDisplaying(listOf("a", "b"), 3, canDisplay, "fallback")).isEqualTo("fallback")
    }

    @Test
    fun textBoxDrawingBlocksAndCyrillicUseTheBundledMonospaceFont() {
        for (char in "aZ09─│╭╯█▀▐▛Привіт←→·…") {
            assertThat(bundledOnly.fontFor(char.code, Font.PLAIN).family).isEqualTo(bundledOnly.regular.family)
        }
    }

    @Test
    fun statusGlyphsMissingFromTheMonospaceFontAreDrawnByAFontThatHasThem() {
        for (char in "⏵⏸❯●✻✢✽✶✓✗⚠◐◑✔★☐") {
            val font = bundledOnly.fontFor(char.code, Font.PLAIN)
            assertThat(font.canDisplay(char.code)).describedAs("U+%04X".format(char.code)).isTrue()
        }
    }

    @Test
    fun brailleSpinnerFramesAreDisplayable() {
        for (code in 0x2800..0x28FF step 17) {
            assertThat(bundledOnly.fontFor(code, Font.PLAIN).canDisplay(code)).describedAs("U+%04X".format(code)).isTrue()
        }
    }

    @Test
    fun boldAndItalicStylesPickTheMatchingFace() {
        assertThat(bundledOnly.fontFor('a'.code, Font.BOLD).fontName).contains("Bold")
        assertThat(bundledOnly.fontFor('a'.code, Font.ITALIC).fontName).contains("Italic")
        assertThat(bundledOnly.fontFor('a'.code, Font.PLAIN).fontName).doesNotContain("Bold", "Italic")
    }

    @Test
    fun cellWidthSizingGivesAWholePixelAdvance() {
        val font = GlyphFontChain(fontSizeForCellWidth(8), emptySet()).regular
        val advance = java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB).createGraphics()
            .let { it.getFontMetrics(font).charWidth('W') }
        assertThat(advance).isEqualTo(8)
    }
}
