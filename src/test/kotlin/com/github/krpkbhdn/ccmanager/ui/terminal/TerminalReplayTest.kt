package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

private const val COLUMNS = 120
private const val ROWS = 32
private const val CELL_WIDTH = 8
private val STEP_MARKER = Regex("\n<<STEP ([^>]*)>>\n")
private val CAPTURE = File(System.getenv("CCM_CAPTURE") ?: "C:/Users/Bohdan/Work/Code/Personal/ccm-shots/terminal-native/claude-start.bin")

/** Replays output captured from a real claude (DevPtyCapture) into the emulator and renders the screen after every captured step. */
class TerminalReplayTest {

    @Test
    fun realClaudeCaptureReplaysIntoReadableScreens() {
        if (!CAPTURE.exists()) return
        val capture = String(CAPTURE.readBytes(), Charsets.UTF_8)
        val steps = STEP_MARKER.findAll(capture).map { it.groupValues[1] }.toList()
        val chunks = capture.split(STEP_MARKER).drop(1)
        val connector = ScriptedConnector()
        val session = TerminalSession(connector)
        val height = ROWS * TerminalFonts(CELL_WIDTH).cellHeight + 16
        TerminalScene(session, connector, COLUMNS * CELL_WIDTH + 24, height).use { scene ->
            chunks.forEachIndexed { index, chunk ->
                scene.play(chunk).save(File(shotDirectory(), "claude-step-${index + 1}-${steps[index].replace(Regex("[^a-z0-9]"), "_")}.png"))
            }
            assertThat(session.columns).isEqualTo(COLUMNS)
            assertThat(session.rows).isEqualTo(ROWS)
        }
        session.close()
    }
}
