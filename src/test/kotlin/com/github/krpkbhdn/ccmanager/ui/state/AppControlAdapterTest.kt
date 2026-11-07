package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.ui.PreviewTerminal
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
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
    fun `attachSession records the folder the session runs in`() {
        val folder = java.nio.file.Path.of("task-folder")

        adapter.attachSession(LaunchedSession("s2", 7, "claude", null, folder))

        assertThat(state.sessions.sessionDirectories["s2"]).isEqualTo(folder)
    }

    @Test
    fun `queuePrompt adds the trimmed text to the session queue`() {
        adapter.queuePrompt("s-working", "  go on ")

        assertThat(state.queue.of("s-working").map { it.text }).containsExactly("go on")
    }

    @Test
    fun `a call that started before the timeout is awaited and returns normally`() {
        val slow = AppControlAdapter(state, scope, Duration.ofMillis(100))
        val finished = java.util.concurrent.atomic.AtomicBoolean()

        slow.onUi { Thread.sleep(150); finished.set(true) }

        assertThat(finished).isTrue()
    }

    @Test
    fun `a call that started but never finishes fails instead of hanging`() {
        val release = java.util.concurrent.CountDownLatch(1)
        val hung = AppControlAdapter(state, scope, Duration.ofMillis(50))

        assertThatThrownBy { hung.onUi { release.await() } }.hasMessageContaining("started but did not finish")
        release.countDown()
    }

    @Test
    fun `a call that timed out is cancelled and does not run later`() {
        val release = java.util.concurrent.CountDownLatch(1)
        val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
        val slow = CoroutineScope(SupervisorJob() + executor.asCoroutineDispatcher())
        executor.execute { release.await() }

        assertThatThrownBy { AppControlAdapter(state, slow, Duration.ofMillis(50)).navigate("teams") }.hasMessageContaining("did not respond")
        release.countDown()
        executor.submit {}.get()

        assertThat(state.route).isNotEqualTo(Route.Teams)
        slow.cancel()
        executor.shutdown()
    }

    @Test
    fun `a call fails clearly when the UI does not run it in time`() {
        val stuck = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined).also { it.cancel() }

        assertThatThrownBy { AppControlAdapter(state, stuck, Duration.ofMillis(50)).navigate("overview") }
            .isInstanceOf(IllegalStateException::class.java).hasMessageContaining("did not respond")
    }
}
