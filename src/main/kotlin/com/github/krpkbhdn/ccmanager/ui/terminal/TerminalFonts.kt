package com.github.krpkbhdn.ccmanager.ui.terminal

import org.jetbrains.skia.Data
import org.jetbrains.skia.Font
import org.jetbrains.skia.FontEdging
import org.jetbrains.skia.FontHinting
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontStyle
import org.jetbrains.skia.Typeface
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val BUNDLED_MONO = "JetBrainsMonoNL"
private const val BUNDLED_SYMBOLS = "NotoSansSymbols2-Regular"
private const val EMOJI_LANGUAGE_TAG = "und-Zsye"
private const val ASCII_LAST = 0x7E

/** JetBrains Mono advances 0.6 em, so a whole-pixel cell width gives a font size whose glyphs fill exactly one cell. */
internal const val MONO_ADVANCE_EM = 0.6f

/** Fonts shipped in resources/fonts (OFL), loaded once and shared by all terminals. */
internal object BundledTypefaces {
    private val mono: Map<Int, Typeface> by lazy {
        mapOf(
            STYLE_PLAIN to load("$BUNDLED_MONO-Regular"),
            STYLE_BOLD to load("$BUNDLED_MONO-Bold"),
            STYLE_ITALIC to load("$BUNDLED_MONO-Italic"),
            STYLE_BOLD or STYLE_ITALIC to load("$BUNDLED_MONO-BoldItalic"),
        )
    }
    val symbols: Typeface by lazy { load(BUNDLED_SYMBOLS) }

    fun mono(styleBits: Int): Typeface = mono.getValue(styleBits)

    private fun load(name: String): Typeface {
        val bytes = BundledTypefaces::class.java.getResourceAsStream("/fonts/$name.ttf")?.use { it.readBytes() }
            ?: error("Bundled font $name.ttf is missing from resources")
        return checkNotNull(FontMgr.default.makeFromData(Data.makeFromBytes(bytes))) { "Bundled font $name.ttf cannot be read" }
    }

    const val STYLE_PLAIN = 0
    const val STYLE_BOLD = 1
    const val STYLE_ITALIC = 2
}

/**
 * The faces of one terminal at one size, all laid out on the same cell grid: bundled mono for text, then the bundled
 * symbol face and whatever system font Skia finds for a glyph that mono lacks (CJK, colour emoji).
 */
internal class TerminalFonts(val cellWidth: Int) {
    val fontSize: Float = cellWidth / MONO_ADVANCE_EM
    private val faces = HashMap<Int, Font>()
    private val fallbackFaces = HashMap<Long, Font>()

    val cellHeight: Int
    val baseline: Float

    init {
        val metrics = face(0).metrics
        cellHeight = ceil(metrics.descent - metrics.ascent + metrics.leading).toInt()
        baseline = (-metrics.ascent + (cellHeight - (metrics.descent - metrics.ascent)) / 2).roundToInt().toFloat()
    }

    fun face(styleBits: Int): Font = faces.getOrPut(styleBits) { Font(BundledTypefaces.mono(styleBits), fontSize).configured() }

    /** True when the glyph is drawn from the mono face as part of a run of text on the cell grid. */
    fun isRunGlyph(codePoint: Int, styleBits: Int): Boolean =
        codePoint <= ASCII_LAST || (!Character.isEmojiPresentation(codePoint) && face(styleBits).getUTF32Glyph(codePoint).toInt() != 0)

    /** Font for a glyph that does not take part in a run: the first face that has it, mono when none does. */
    fun faceFor(codePoint: Int, styleBits: Int): Font {
        val emoji = Character.isEmojiPresentation(codePoint)
        if (!emoji && face(styleBits).getUTF32Glyph(codePoint).toInt() != 0) return face(styleBits)
        return fallbackFaces.getOrPut(codePoint.toLong() shl STYLE_SHIFT or styleBits.toLong()) {
            fallbackTypeface(codePoint, emoji)?.let { Font(it, fontSize).configured() } ?: face(styleBits)
        }
    }

    private fun fallbackTypeface(codePoint: Int, emoji: Boolean): Typeface? {
        val symbols = BundledTypefaces.symbols
        if (!emoji && Font(symbols, fontSize).getUTF32Glyph(codePoint).toInt() != 0) return symbols
        val language = if (emoji) arrayOf(EMOJI_LANGUAGE_TAG) else null
        return FontMgr.default.matchFamilyStyleCharacter(null, FontStyle.NORMAL, language, codePoint)
    }

    private fun Font.configured() = apply {
        edging = FontEdging.ANTI_ALIAS
        hinting = FontHinting.NONE
        isSubpixel = true
        isBaselineSnapped = true
    }

    private companion object {
        const val STYLE_SHIFT = 8
    }
}
