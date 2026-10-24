package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.io.File

private const val ESC = "\u001b"
private const val CLAUDE_ORANGE = 0xD77757
private const val WIDTH = 1000
private const val HEIGHT = 640
private const val PADDING_X = 12
private const val PADDING_Y = 8
private const val CELL_WIDTH = 8
private val GLYPH_SHEET = listOf(
    "${ESC}[2J${ESC}[H",
    "${ESC}[38;2;215;119;87m ▐▛███▜▌ ${ESC}[1mClaude Code${ESC}[0m ${ESC}[38;2;153;153;153mv2.1.310${ESC}[0m",
    "${ESC}[38;2;215;119;87m ▝▜█████▛▘ ${ESC}[0m Sonnet 5.5 · Claude Team",
    "${ESC}[38;5;208m██████${ESC}[0m 256-colour 208   ${ESC}[48;5;24;38;5;231m white on 24 ${ESC}[0m   ${ESC}[48;2;47;68;104m truecolour bg ${ESC}[0m",
    (30..37).joinToString("") { "${ESC}[${it}m██${ESC}[0m" } + " " + (90..97).joinToString("") { "${ESC}[${it}m██${ESC}[0m" },
    "${ESC}[1mbold${ESC}[0m ${ESC}[3mitalic${ESC}[0m ${ESC}[1;3mbold italic${ESC}[0m ${ESC}[4munderline${ESC}[0m ${ESC}[2mdim${ESC}[0m ${ESC}[7minverse${ESC}[0m ${ESC}[8mhidden${ESC}[0m ${ESC}[31;1mbright red${ESC}[0m",
    "Wide: 漢字かな한글   Emoji: \uD83D\uDE00 \uD83D\uDE80 ✅ ❌ ⚠️ \uD83C\uDF89 end",
    "Symbols: ⏵⏵ ❯ ● ✻ ✢ ⎿ ✓ ⚠ ⏺ ✔ → ← ↑ ↓ · … ░▒▓ █▀▄▌▐ ▘▝▖▗",
    "Boxes: ╭──────╮ ┌─┬─┐ ╔═╦═╗ ┏━┳━┓",
    "       │ hi   │ ├─┼─┤ ║ ║ ║ ┃ ┃ ┃",
    "       ╰──────╯ └─┴─┘ ╚═╩═╝ ┗━┻━┛",
    "Ukrainian: Привіт, світе! ЇїІіЄєҐґ  Russian: Съешь ещё этих мягких французских булок",
    "ASCII: The quick brown fox jumps over the lazy dog 0123456789 {}[]()<>=+-*/\\|;:'\",.?!@#\$%^&_~`",
    "",
    "${ESC}[38;2;215;119;87m────────────────────────────────────────────────────────────────${ESC}[0m",
    "${ESC}[38;2;215;119;87m> ${ESC}[0mtype here",
)

class TerminalRenderingTest {
    private val connector = ScriptedConnector()
    private val session = TerminalSession(connector)
    private val scene = TerminalScene(session, connector, WIDTH, HEIGHT)

    @AfterEach
    fun close() {
        scene.close()
        session.close()
    }

    private fun cellLeft(column: Int) = PADDING_X + column * CELL_WIDTH

    @Test
    fun glyphSheetRendersInDarkTheme() {
        val image = scene.play(GLYPH_SHEET.joinToString("\r\n"))
        image.save(File(shotDirectory(), "glyph-sheet-dark.png"))
        val bitmap = image.toBitmap()
        assertThat(bitmap.count(CLAUDE_ORANGE)).isGreaterThan(400)
        assertThat(bitmap.count(0xFF8700)).isGreaterThan(100)
        assertThat(bitmap.rgbAt(WIDTH - 2, HEIGHT - 2)).isEqualTo(DARK_BACKGROUND.toRgb())
    }

    @Test
    fun glyphSheetRendersInLightTheme() {
        val lightConnector = ScriptedConnector()
        val lightSession = TerminalSession(lightConnector)
        TerminalScene(lightSession, lightConnector, WIDTH, HEIGHT, TerminalTheme(dark = false)).use { light ->
            val image = light.play(GLYPH_SHEET.joinToString("\r\n"))
            image.save(File(shotDirectory(), "glyph-sheet-light.png"))
            assertThat(image.toBitmap().rgbAt(WIDTH - 2, HEIGHT - 2)).isEqualTo(LIGHT_BACKGROUND.toRgb())
        }
        lightSession.close()
    }

    @Test
    fun glyphSheetRendersOnAHighDensityScreen() {
        val denseConnector = ScriptedConnector()
        val denseSession = TerminalSession(denseConnector)
        TerminalScene(denseSession, denseConnector, WIDTH * 2, HEIGHT * 2, density = 2f).use { dense ->
            val image = dense.play(GLYPH_SHEET.joinToString("\r\n"))
            image.save(File(shotDirectory(), "glyph-sheet-dark-2x.png"))
            assertThat(image.toBitmap().count(CLAUDE_ORANGE)).isGreaterThan(1600)
        }
        denseSession.close()
    }

    @Test
    fun stackedBlocksJoinWithoutSeams() {
        val image = scene.play("${ESC}[2J${ESC}[H${ESC}[38;2;215;119;87m" + (1..4).joinToString("\r\n") { "██████████" })
        image.save(File(shotDirectory(), "blocks.png"))
        val bitmap = image.toBitmap()
        val left = cellLeft(0)
        val top = PADDING_Y
        val height = 4 * scene.cellHeight
        val seams = bitmap.count(CLAUDE_ORANGE, left until left + 10 * CELL_WIDTH, top until top + height)
        assertThat(seams).isEqualTo(10 * CELL_WIDTH * height)
    }

    @Test
    fun cursorShapesAreDrawnFilledAndHollow() {
        scene.play("${ESC}[2J${ESC}[Hcursor")
        scene.image().save(File(shotDirectory(), "cursor-block-unfocused.png"))
        session.focused = true
        scene.image().save(File(shotDirectory(), "cursor-block-focused.png"))
        scene.play("${ESC}[5 q").save(File(shotDirectory(), "cursor-bar.png"))
        scene.play("${ESC}[3 q").save(File(shotDirectory(), "cursor-underline.png"))
    }

    @Test
    fun selectionHighlightsTheChosenCells() {
        scene.play("${ESC}[2J${ESC}[Hselect this text please")
        session.select(CellSelection(Cell(7, 0), Cell(10, 0)))
        val image = scene.image()
        image.save(File(shotDirectory(), "selection.png"))
        val selected = image.toBitmap().count(DARK_SELECTION.toRgb(), cellLeft(7) until cellLeft(11), PADDING_Y until PADDING_Y + scene.cellHeight)
        assertThat(selected).isGreaterThan(4 * CELL_WIDTH * scene.cellHeight / 2)
    }

    private fun Color.toRgb() = toArgb() and 0xFFFFFF
}
