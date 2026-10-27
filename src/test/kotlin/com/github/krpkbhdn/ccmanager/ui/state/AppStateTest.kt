package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.ui.await
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify

private const val PROJECT_ID = 1L

class AppStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val closedSessions = CopyOnWriteArrayList<String>()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) = Unit
        override fun close(ccmSessionId: String) {
            closedSessions += ccmSessionId
        }

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val world = PreviewWorld()
    private val backend = world.backend(terminal)
    private val state = AppState(backend, scope, UiSettingsStore(null))

    @BeforeEach
    fun start() {
        state.start()
        await { state.live.sessions.isNotEmpty() && state.projects.all.isNotEmpty() }
    }

    @AfterEach
    fun stop() {
        state.dispose()
        scope.cancel()
    }

    @Test
    fun `removing a project closes its live sessions and forgets the project`() {
        val project = state.projects.all.first { it.id == PROJECT_ID }
        val liveIds = state.live.of(PROJECT_ID).map { it.ccmSessionId }
        state.navigate(Route.Project(PROJECT_ID))
        assertThat(liveIds).isNotEmpty()

        state.projects.requestRemove(project)
        assertThat(state.confirm!!.message).contains("running sessions will be terminated")
        state.confirm!!.onConfirm()
        await { closedSessions.containsAll(liveIds) }

        verify(backend.projects).remove(PROJECT_ID)
        await { state.route == Route.Overview }
    }

    @Test
    fun `removing a project without sessions does not warn about terminating`() {
        val project = state.projects.all.first { state.live.of(it.id).isEmpty() }

        state.projects.requestRemove(project)

        assertThat(state.confirm!!.message).doesNotContain("terminated")
    }

    @Test
    fun `opening a project offers its author setup`() {
        val projectSetup = AuthorEntry(AuthorComponent.PROJECT_SETUP, PROJECT_ID, "Project setup", "summary", 1, 0, AuthorStatus.NOT_INSTALLED, emptyList(), false)
        `when`(backend.author.entries(PROJECT_ID)).thenReturn(listOf(projectSetup))

        state.navigate(Route.Project(PROJECT_ID))

        await { state.author.offer != null }
        assertThat(state.author.offer!!.entries).containsExactly(projectSetup)
    }
}
