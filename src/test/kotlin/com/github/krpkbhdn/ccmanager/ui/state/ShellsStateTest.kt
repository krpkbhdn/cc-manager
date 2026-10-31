package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.shell.ShellProfile
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.pty4j.PtyProcess
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify

class ShellsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chosen = CopyOnWriteArrayList<String>()
    private val errors = CopyOnWriteArrayList<String>()
    private val openFails = object : TerminalPort by PreviewTerminal {
        override fun openShell(shell: LaunchedShell) = throw IllegalStateException("no terminal")
    }
    private val backend = PreviewWorld().backend(openFails)
    private val shells = ShellsState(
        backend, scope,
        openShellsOf = { emptyList() }, lastChosen = { null }, onChosen = { chosen += it },
        onOpened = { _, _ -> }, onClosed = { _, _ -> }, reportError = { errors += it },
    )

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `a failed launch reports the error and keeps the default shell`() {
        shells.open(1, ShellProfile("Git Bash", Path.of("bash.exe"), emptyList()))

        val deadline = System.currentTimeMillis() + 5_000
        while (errors.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)

        assertThat(errors).hasSize(1)
        assertThat(chosen).isEmpty()
    }

    @Test
    fun `a terminal that fails to open removes the label, kills the process and reports the error`() {
        val process = mock(PtyProcess::class.java)
        val profile = ShellProfile("Git Bash", Path.of("bash.exe"), emptyList())
        val launched = LaunchedShell("shell-1", 1, profile, process)
        doReturn(launched).`when`(backend.shellLauncher).launch(1L, profile)

        shells.open(1, profile)

        val deadline = System.currentTimeMillis() + 5_000
        while (errors.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(errors).hasSize(1)
        assertThat(shells.owns("shell-1")).isFalse()
        verify(process).destroyForcibly()
    }
}
