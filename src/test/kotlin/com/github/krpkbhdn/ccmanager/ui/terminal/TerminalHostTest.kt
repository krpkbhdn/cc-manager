package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.pty4j.PtyProcess
import java.io.ByteArrayOutputStream
import java.io.PipedInputStream
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

private const val JOIN_MILLIS = 10_000L

class TerminalHostTest {
    private fun process(): PtyProcess = mock(PtyProcess::class.java).also {
        `when`(it.inputStream).thenReturn(PipedInputStream())
        `when`(it.outputStream).thenReturn(ByteArrayOutputStream())
        `when`(it.isAlive).thenReturn(true)
        `when`(it.pid()).thenReturn(Long.MAX_VALUE)
    }

    @Test
    fun `opening an open session with the same process keeps it alive`() {
        val process = process()
        val session = LaunchedSession("ccm-host-same", 1, "claude", process)
        TerminalHost.open(session)

        TerminalHost.open(session)

        verify(process, never()).destroyForcibly()
        assertThat(TerminalHost.sessionOf("ccm-host-same")).isNotNull()
        TerminalHost.close("ccm-host-same")
    }

    @Test
    fun `a close that fails does not leave the host closing`() {
        val process = process()
        `when`(process.isAlive).thenThrow(IllegalStateException("boom"))
        TerminalHost.open(LaunchedSession("ccm-host-fail", 1, "claude", process))

        TerminalHost.close("ccm-host-fail")

        Thread.getAllStackTraces().keys.filter { it.name.startsWith("TerminalClose-") }.forEach { it.join(JOIN_MILLIS) }
        assertThat(TerminalHost.closing()).isFalse()
    }

    @Test
    fun `opening an open session with another process kills the new one`() {
        val first = process()
        val second = process()
        TerminalHost.open(LaunchedSession("ccm-host-other", 1, "claude", first))

        TerminalHost.open(LaunchedSession("ccm-host-other", 1, "claude", second))

        verify(second).destroyForcibly()
        verify(first, never()).destroyForcibly()
        TerminalHost.close("ccm-host-other")
    }
}
