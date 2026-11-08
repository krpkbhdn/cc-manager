package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.shell.ShellProfile
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.shell.LaunchedShell
import com.pty4j.PtyProcess
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
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
import org.mockito.Mockito.`when`

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

        await { errors.isNotEmpty() }

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

        await { errors.isNotEmpty() }
        assertThat(errors).hasSize(1)
        assertThat(shells.owns("shell-1")).isFalse()
        verify(process).destroyForcibly()
    }

    @Test
    fun `shells opened in a folder are found and closed by that folder only`() {
        val processExit = CountDownLatch(1)
        val closed = CopyOnWriteArrayList<Pair<Long, String>>()
        val profile = ShellProfile("Git Bash", Path.of("bash.exe"), emptyList())
        val here = Path.of("repo-task-x")
        val there = Path.of("repo-task-y")
        val working = PreviewWorld().backend(PreviewTerminal)
        doReturn(listOf(profile)).`when`(working.shellDetector).detect()
        doReturn(launchedShell("shell-here", profile, processExit)).`when`(working.shellLauncher).launch(1L, profile, here)
        doReturn(launchedShell("shell-there", profile, processExit)).`when`(working.shellLauncher).launch(1L, profile, there)
        val state = ShellsState(
            working, scope,
            openShellsOf = { emptyList() }, lastChosen = { null }, onChosen = { },
            onOpened = { _, _ -> }, onClosed = { projectId, id -> closed += projectId to id }, reportError = { errors += it },
        )
        state.detect()
        await { state.default != null }
        state.openIn(1, here)
        state.openIn(1, there)
        await { state.hasShellIn(here) && state.hasShellIn(there) }

        state.closeIn(here.toAbsolutePath().resolve("..").resolve("repo-task-x"))

        assertThat(state.hasShellIn(here)).isFalse()
        assertThat(state.hasShellIn(there)).isTrue()
        assertThat(closed).containsExactly(1L to "shell-here")
        processExit.countDown()
    }

    private fun launchedShell(id: String, profile: ShellProfile, exit: CountDownLatch): LaunchedShell {
        val process = mock(PtyProcess::class.java)
        `when`(process.waitFor()).thenAnswer { exit.await(); 0 }
        return LaunchedShell(id, 1, profile, process)
    }
}
