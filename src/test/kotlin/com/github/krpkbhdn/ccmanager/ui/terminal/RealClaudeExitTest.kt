package com.github.krpkbhdn.ccmanager.ui.terminal

import com.pty4j.PtyProcess
import com.pty4j.PtyProcessBuilder
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test

private const val WAIT_MILLIS = 60_000L
private const val POLL_MILLIS = 100L
private const val SETTLE_MILLIS = 2_000L
private const val COLUMNS = 120
private const val ROWS = 32

/** Real claude (no prompt is ever sent): the exit keys must end it within the grace period without the kill. */
class RealClaudeExitTest {

    @Test
    fun idleClaudeExitsOnItsOwn() = assertExitsByItself(typed = "")

    @Test
    fun claudeWithUnsentInputExitsOnItsOwn() = assertExitsByItself(typed = "draft text")

    private fun assertExitsByItself(typed: String) {
        assumeTrue(System.getenv("CCM_REAL_CLAUDE") == "1")
        val process = startClaude()
        val session = TerminalSession(PtyTtyConnector(process, "claude") { })
        try {
            awaitFirstFrame(session)
            if (typed.isNotEmpty()) {
                session.send(typed)
                Thread.sleep(SETTLE_MILLIS)
            }
            requestExit(session)
            assertThat(process.waitFor(EXIT_GRACE_MILLIS, TimeUnit.MILLISECONDS))
                .describedAs("claude exited within ${EXIT_GRACE_MILLIS}ms, screen: ${session.screenText().lines().filter { it.isNotBlank() }}").isTrue()
        } finally {
            if (process.isAlive) process.destroyForcibly()
            session.close()
        }
    }

    private fun startClaude(): PtyProcess =
        PtyProcessBuilder(arrayOf(CLAUDE_EXE)).setDirectory(SANDBOX_DIR).setEnvironment(cleanDevEnvironment())
            .setUseWinConPty(true).setInitialColumns(COLUMNS).setInitialRows(ROWS).start()

    private fun awaitFirstFrame(session: TerminalSession) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        fun drawn() = session.screenText().let { it.contains("❯") || it.contains("for shortcuts") }
        while (!drawn() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertThat(drawn()).describedAs("first frame, screen: ${session.screenText().lines().filter { it.isNotBlank() }}").isTrue()
        Thread.sleep(SETTLE_MILLIS)
    }
}
