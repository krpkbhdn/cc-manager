package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.ui.await
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.ui.palette.paletteItems
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

    @Test
    fun `opening the assistant session routes to the assistant screen and the assistant is named`() {
        val assistant = SessionSnapshot(
            "ccm-assistant", ClaudeLauncher.ASSISTANT_PROJECT_ID, "c0", AgentStatus.IDLE, null, null, null, null,
            Instant.now(), Instant.now(), emptyList(), 0, null,
        )
        `when`(backend.sessions.sessions()).thenReturn(world.sessions + assistant)
        world.fire()
        await { state.live.find("ccm-assistant") != null }

        state.openSession("ccm-assistant")

        assertThat(state.route).isEqualTo(Route.Assistant)
        assertThat(state.assistant.liveSessionId).isEqualTo("ccm-assistant")
        assertThat(state.projects.nameOf(ClaudeLauncher.ASSISTANT_PROJECT_ID)).isEqualTo("Assistant")
    }

    @Test
    fun `the palette entry for assistant startup shows the mode and cycles it`() {
        fun entry() = paletteItems(state, false).first { it.id == "assistant-startup" }
        assertThat(entry().title).endsWith("Ask every time")

        entry().run()

        assertThat(state.settings.assistantStartup).isEqualTo(AssistantStartup.AUTO_START)
        assertThat(entry().title).endsWith("Start automatically")
    }
}
