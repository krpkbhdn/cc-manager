package com.github.krpkbhdn.ccmanager.ui.terminal

import com.jediterm.core.util.TermSize
import com.jediterm.terminal.TextStyle
import com.jediterm.terminal.TtyConnector
import java.util.concurrent.atomic.AtomicInteger
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

private const val WAIT_MILLIS = 5_000L

class TerminalEmulatorTest {
    private class FailingOnceConnector : TtyConnector {
        val reads = AtomicInteger()

        @Volatile
        var closed = false

        override fun read(buf: CharArray, offset: Int, length: Int): Int = when (reads.getAndIncrement()) {
            0 -> throw IllegalStateException("bad chunk")
            1 -> "after".also { it.toCharArray(buf, offset, 0, it.length) }.length
            else -> { closed = true; -1 }
        }

        override fun write(bytes: ByteArray) = Unit
        override fun write(string: String) = Unit
        override fun isConnected() = !closed
        override fun resize(termSize: TermSize) = Unit
        override fun waitFor() = 0
        override fun ready() = true
        override fun getName() = "failing-once"
        override fun close() { closed = true }
    }

    @Test
    fun `an unexpected exception while reading does not end the output`() {
        val connector = FailingOnceConnector()
        val emulator = TerminalEmulator(connector, 40, 5, TextStyle.EMPTY, {})

        emulator.start()

        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (System.currentTimeMillis() < deadline && !emulator.buffer.getScreenLines().contains("[Session ended")) Thread.sleep(20)
        assertThat(emulator.buffer.getScreenLines()).contains("after").contains("[Session ended (exit code 0)]")
        emulator.close()
    }
}
