package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.terminal.TtyConnector
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TerminalBlockedWriteTest {
    @Test
    fun `a write that blocks does not stall resizes`() {
        val release = CountDownLatch(1)
        val scripted = ScriptedConnector()
        val connector = object : TtyConnector by scripted {
            override fun write(bytes: ByteArray) {
                release.await(10, TimeUnit.SECONDS)
            }
        }
        val session = TerminalSession(connector)
        try {
            session.send("x".repeat(1000))
            session.fit(100, 30)

            session.awaitModel { session.columns == 100 }

            assertThat(session.columns).isEqualTo(100)
        } finally {
            release.countDown()
            session.close()
        }
    }
}
