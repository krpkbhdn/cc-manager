package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class AppControlAdapterTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val state = AppState(PreviewWorld().backend(PreviewTerminal), scope, UiSettingsStore(null))
    private val adapter = AppControlAdapter(state, scope, Duration.ofSeconds(5))

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `navigate switches the route and rejects an unknown one`() {
        adapter.navigate("teams")

        assertThat(state.route).isEqualTo(Route.Teams)
        assertThatThrownBy { adapter.navigate("nowhere") }.isInstanceOf(IllegalStateException::class.java).hasMessageContaining("nowhere")
    }

    @Test
    fun `openProject selects the tab of a known project only`() {
        state.start()
        com.github.krpkbhdn.ccmanager.ui.await { state.projects.all.isNotEmpty() }
        val id = state.projects.all.first().id

        adapter.openProject(id, "git")

        assertThat(state.route).isEqualTo(Route.Project(id))
        assertThat(state.viewOf(id).selected).isEqualTo(ProjectTab.Git)
        assertThatThrownBy { adapter.openProject(id, "bogus") }.isInstanceOf(IllegalStateException::class.java)
        assertThatThrownBy { adapter.openProject(-5, "git") }.hasMessageContaining("Unknown project")
        state.dispose()
    }

    @Test
    fun `attachSession opens the session tab of its project`() {
        adapter.attachSession(LaunchedSession("s1", 7, "claude", null))

        assertThat(state.viewOf(7).openSessions).containsExactly("s1")
    }

    @Test
    fun `queuePrompt adds the trimmed text to the session queue`() {
        adapter.queuePrompt("s-working", "  go on ")

        assertThat(state.queue.of("s-working").map { it.text }).containsExactly("go on")
    }

    @Test
    fun `a call fails clearly when the UI does not run it in time`() {
        val stuck = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { it.cancel() }

        assertThatThrownBy { AppControlAdapter(state, stuck, Duration.ofMillis(50)).navigate("overview") }
            .isInstanceOf(IllegalStateException::class.java).hasMessageContaining("did not respond")
    }
}
