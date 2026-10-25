package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val CAP = 30

class TerminalScrollbackCapTest {
    private val connector = ScriptedConnector()
    private val session = TerminalSession(connector, CAP)

    @AfterEach
    fun close() = session.close()

    private fun play(text: String) {
        connector.feed(text)
        settle(connector, session)
    }

    @Test
    fun viewAndSelectionFollowTheContentOncePastTheScrollbackCap() {
        play((1..100).joinToString("\r\n") { "row $it" })
        assertThat(session.scrollbackLines).isEqualTo(CAP)
        session.scrollJumpTo(10f)
        val last = session.rows - 1
        session.select(CellSelection(Cell(0, last), Cell(4, last)))
        val text = session.selection!!.textIn(session.buffer)

        play("\r\nmore\r\nmore")

        assertThat(session.scrollbackLines).isEqualTo(CAP)
        assertThat(session.scrollTarget).isEqualTo(12f)
        assertThat(session.selection!!.textIn(session.buffer)).isEqualTo(text)
    }
}
