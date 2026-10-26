package com.github.krpkbhdn.ccmanager.ui.run

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.run.ActiveRun
import com.github.krpkbhdn.ccmanager.run.RunConfiguration
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService
import com.github.krpkbhdn.ccmanager.run.RunService
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import java.nio.file.Path
import java.util.Optional
import java.util.OptionalInt
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class RunStateTest {
    private val directory = Path.of("work", "app").toAbsolutePath()
    private val configurations = mock(RunConfigurationService::class.java)
    private val runs = mock(RunService::class.java)
    private val events = CopyOnWriteArrayList<String>()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) { events += "open:${session.ccmSessionId()}" }
        override fun send(ccmSessionId: String, text: String) = Unit
        override fun close(ccmSessionId: String) { events += "close:$ccmSessionId" }

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private val state = RunState(configurations, runs, terminal, scope, { directory }, { }, { }, { })
    private var configurationList = listOf(RunConfiguration(7, 1, "dev", "npm run dev", directory))
    private var activeRun: ActiveRun? = null

    init {
        `when`(configurations.list(anyLong())).thenAnswer { configurationList }
        `when`(runs.current(anyLong())).thenAnswer { Optional.ofNullable(activeRun) }
    }

    @AfterEach
    fun stop() = scope.cancel()

    private fun runWithId(runId: String) = ActiveRun(7, runId, "dev", null, OptionalInt.empty())

    private fun reload() = state.track(1)

    private fun awaitEvents(vararg expected: String) {
        val deadline = System.nanoTime() + 5_000_000_000L
        while (events.toList() != expected.toList() && System.nanoTime() < deadline) Thread.sleep(10)
        assertThat(events).containsExactly(*expected)
    }

    @Test
    fun `a run started elsewhere gets a console`() {
        activeRun = runWithId("run-7-1")
        reload()
        awaitEvents("open:run-7-1")
    }

    @Test
    fun `a restart opens the new console and closes the old one`() {
        activeRun = runWithId("run-7-1")
        reload()
        awaitEvents("open:run-7-1")
        activeRun = runWithId("run-7-2")
        reload()
        awaitEvents("open:run-7-1", "open:run-7-2", "close:run-7-1")
    }

    @Test
    fun `a deleted configuration closes its console`() {
        activeRun = runWithId("run-7-1")
        reload()
        awaitEvents("open:run-7-1")
        configurationList = emptyList()
        reload()
        awaitEvents("open:run-7-1", "close:run-7-1")
    }
}
