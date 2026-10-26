package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.shell.ShellProfile
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ShellsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val chosen = CopyOnWriteArrayList<String>()
    private val errors = CopyOnWriteArrayList<String>()
    private val shells = ShellsState(
        PreviewWorld().backend(PreviewTerminal), scope,
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
}
