package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.ui.await
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
import com.github.krpkbhdn.ccmanager.ui.palette.paletteItems
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
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
private const val GONE_PROJECT_ID = 987654L

class AppStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val closedSessions = CopyOnWriteArrayList<String>()
    private val typed = CopyOnWriteArrayList<Pair<String, String>>()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) {
            typed += ccmSessionId to text
        }
        override fun close(ccmSessionId: String) {
            closedSessions += ccmSessionId
        }

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val world = PreviewWorld()
    private val backend = world.backend(terminal)
    private val webHosts = CopyOnWriteArrayList<FakePageHost>()
    private val state = AppState(backend, scope, UiSettingsStore(null)) { FakePageHost().also { webHosts += it } }

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
    fun `voice settings exist when the backend has voice services`() {
        assertThat(state.voice).isNotNull()
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
    fun `a project list reload closes the web browser of a project that is gone and keeps the others`() {
        state.web.view(PROJECT_ID)
        state.web.view(GONE_PROJECT_ID)
        assertThat(webHosts).hasSize(2)

        backend.projects.rename(PROJECT_ID, "renamed")

        await { webHosts[1].closed }
        assertThat(webHosts[0].closed).isFalse()
    }

    @Test
    fun `removing a project without sessions does not warn about terminating`() {
        val project = state.projects.all.first { state.live.of(it.id).isEmpty() }

        state.projects.requestRemove(project)

        assertThat(state.confirm!!.message).doesNotContain("terminated")
    }

    @Test
    fun `opening the assistant session routes to the assistant screen and the assistant is named`() {
        val assistant = SessionSnapshot(
            "ccm-assistant", ClaudeLauncher.ASSISTANT_PROJECT_ID, "c0", AgentStatus.IDLE, null, null, null, null,
            Instant.now(), Instant.now(), emptyList(), 0, null,
        )
        org.mockito.Mockito.doReturn(world.sessions + assistant).`when`(backend.sessions).sessions()
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

    @Test
    fun `compacting the context asks for confirmation before anything is sent`() {
        state.requestCompactContext("ccm-x")

        assertThat(state.confirm?.title).isEqualTo("Compact this session?")
        assertThat(state.confirm?.destructive).isFalse()
        assertThat(typed).isEmpty()
    }

    @Test
    fun `confirming the compact request types the command into the session`() {
        state.requestCompactContext("s-idle")

        state.confirm!!.onConfirm()

        await { typed.contains("s-idle" to Keys.ENTER) }
        assertThat(typed.filter { it.first == "s-idle" }.map { it.second }).containsExactly("/compact", Keys.ENTER)
    }

    @Test
    fun `the palette offers show-review in a project only`() {
        fun ids() = paletteItems(state, false).map { it.id }
        val project = state.projects.all.first { !it.group }.id
        val group = state.projects.all.first { it.group }.id
        assertThat(ids()).doesNotContain("show-review")

        state.navigate(Route.Project(project))
        assertThat(ids()).contains("show-review")
        paletteItems(state, false).first { it.id == "show-review" }.run()
        assertThat(state.viewOf(project).selected).isEqualTo(ProjectTab.Review)

        state.navigate(Route.Project(group))
        assertThat(ids()).doesNotContain("show-review")
    }

    @Test
    fun `the palette offers show-web-review in a project only`() {
        fun ids() = paletteItems(state, false).map { it.id }
        val project = state.projects.all.first { !it.group }.id
        val group = state.projects.all.first { it.group }.id
        assertThat(ids()).doesNotContain("show-web-review")

        state.navigate(Route.Project(project))
        paletteItems(state, false).first { it.id == "show-web-review" }.run()
        assertThat(state.viewOf(project).selected).isEqualTo(ProjectTab.Web)

        state.navigate(Route.Project(group))
        assertThat(ids()).doesNotContain("show-web-review")
    }
}
