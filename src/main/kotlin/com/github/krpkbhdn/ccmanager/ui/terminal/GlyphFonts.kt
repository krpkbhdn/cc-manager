package com.github.krpkbhdn.ccmanager.ui.terminal

import java.awt.Font
import java.awt.GraphicsEnvironment
import java.util.concurrent.ConcurrentHashMap

private const val BUNDLED_MONO = "JetBrainsMonoNL"
private const val BUNDLED_SYMBOLS = "NotoSansSymbols2-Regular"
private val BUNDLED_STYLES = mapOf(
    Font.PLAIN to "Regular", Font.BOLD to "Bold", Font.ITALIC to "Italic", Font.BOLD or Font.ITALIC to "BoldItalic",
)

/** Installed system faces tried after the bundled ones; the logical Dialog font is the final net with Java's own fallback. */
private val NATIVE_SYMBOL_FAMILIES = listOf("Segoe UI Symbol", "Apple Symbols")
private val SYSTEM_SYMBOL_FAMILIES = listOf(
    "Menlo", "DejaVu Sans Mono", "DejaVu Sans", "Cambria Math", "Noto Sans Symbols",
    "Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji", "Arial Unicode MS",
    "Microsoft YaHei", "Yu Gothic", "Malgun Gothic", "MS Gothic", "Apple SD Gothic Neo", "Noto Sans CJK JP",
)
private val SYSTEM_EMOJI_FAMILIES = listOf("Segoe UI Emoji", "Apple Color Emoji", "Noto Color Emoji")

/** First candidate that can display [codePoint]; [fallback] when none can (it draws the font's missing-glyph box). */
internal fun <T> firstDisplaying(candidates: List<T>, codePoint: Int, canDisplay: (T, Int) -> Boolean, fallback: T): T =
    candidates.firstOrNull { canDisplay(it, codePoint) } ?: fallback

/** One font family in the four AWT styles; [canDisplay] consults the plain style only. */
internal class FontFace(private val create: (style: Int) -> Font) {
    private val byStyle = ConcurrentHashMap<Int, Font>()

    fun font(style: Int): Font = byStyle.computeIfAbsent(style and STYLE_MASK) { create(it) }

    fun canDisplay(codePoint: Int): Boolean = font(Font.PLAIN).canDisplay(codePoint)

    private companion object {
        const val STYLE_MASK = Font.BOLD or Font.ITALIC
    }
}

/**
 * Per-glyph font selection for a monospace cell grid: bundled JetBrains Mono for text, then symbol and emoji faces.
 * JediTerm draws with a single AWT font and AWT does not fall back for physical fonts, so without this missing
 * glyphs would show as boxes.
 */
internal class GlyphFontChain(size: Float, installedFamilies: Set<String> = installedFamilyNames()) {
    private val primary = BundledFonts.mono(size)
    private val symbolChain: List<FontFace> =
        listOf(primary) + systemFaces(NATIVE_SYMBOL_FAMILIES, installedFamilies, size) + BundledFonts.symbols(size) +
            systemFaces(SYSTEM_SYMBOL_FAMILIES, installedFamilies, size)
    private val emojiChain: List<FontFace> =
        systemFaces(SYSTEM_EMOJI_FAMILIES, installedFamilies, size) + symbolChain
    private val logical = FontFace { Font(Font.DIALOG, it, 1).deriveFont(size) }
    private val chosen = ConcurrentHashMap<Int, FontFace>()

    val regular: Font get() = primary.font(Font.PLAIN)

    fun fontFor(codePoint: Int, style: Int): Font = chosen.computeIfAbsent(codePoint) { choose(it) }.font(style)

    private fun choose(codePoint: Int): FontFace {
        if (primary.canDisplay(codePoint) && !Character.isEmojiPresentation(codePoint)) return primary
        val chain = if (Character.isEmojiPresentation(codePoint)) emojiChain else symbolChain
        val face = firstDisplaying(chain, codePoint, FontFace::canDisplay, logical)
        return if (face === logical && !logical.canDisplay(codePoint)) primary else face
    }

    private companion object {
        fun systemFaces(families: List<String>, installed: Set<String>, size: Float): List<FontFace> =
            families.filter { it in installed }.map { family -> FontFace { Font(family, it, 1).deriveFont(size) } }
    }
}

internal fun installedFamilyNames(): Set<String> =
    GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toSet()

/** Fonts shipped in resources/fonts (OFL), loaded once and shared by all terminals. */
internal object BundledFonts {
    private val monoFonts: Map<Int, Font> by lazy {
        BUNDLED_STYLES.mapValues { (_, name) -> load("$BUNDLED_MONO-$name") }
    }
    private val symbolsFont: Font by lazy { load(BUNDLED_SYMBOLS) }

    fun mono(size: Float) = FontFace { monoFonts.getValue(it).deriveFont(size) }

    fun symbols(size: Float) = FontFace { symbolsFont.deriveFont(it, size) }

    private fun load(name: String): Font {
        val stream = BundledFonts::class.java.getResourceAsStream("/fonts/$name.ttf")
            ?: error("Bundled font $name.ttf is missing from resources")
        return stream.use { Font.createFont(Font.TRUETYPE_FONT, it) }
    }
}
