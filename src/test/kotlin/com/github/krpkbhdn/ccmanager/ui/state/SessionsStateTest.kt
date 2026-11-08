package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.await
import com.pty4j.PtyProcess
import java.util.concurrent.CopyOnWriteArrayList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class SessionsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val opened = CopyOnWriteArrayList<String>()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) = Unit
        override fun close(ccmSessionId: String) = Unit

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val backend = PreviewWorld().backend(terminal)
    private val team = Team(7, "Dev", emptyList())

    @AfterEach
    fun stop() = scope.cancel()

    private fun sessionsState(defaultTeam: Team?) = SessionsState(
        backend, scope, { error("not used") }, { null }, { defaultTeam },
        onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { error(it) },
    )

    private fun session(id: String) = LaunchedSession(id, 1, "claude", mock(PtyProcess::class.java))

    @Test
    fun `starting a session while another is launching reports an error`() {
        val release = java.util.concurrent.CountDownLatch(1)
        val errors = CopyOnWriteArrayList<String>()
        `when`(backend.launcher.launch(anyLong(), org.mockito.ArgumentMatchers.eq(LaunchMode.NEW), org.mockito.ArgumentMatchers.eq(team))).thenAnswer {
            release.await()
            session("ccm-slow")
        }
        val state = SessionsState(
            backend, scope, { error("not used") }, { null }, { team },
            onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { errors += it },
        )

        state.launch(1, LaunchMode.NEW)
        await { state.launching }
        state.launch(1, LaunchMode.NEW)
        release.countDown()

        assertThat(errors).hasSize(1)
        await { "ccm-slow" in opened }
    }

    @Test
    fun `launch with a default team starts every mode with the team and records it for the session`() {
        for (mode in LaunchMode.entries) {
            val id = "ccm-$mode"
            `when`(backend.launcher.launch(anyLong(), org.mockito.ArgumentMatchers.eq(mode), org.mockito.ArgumentMatchers.eq(team))).thenReturn(session(id))
            val state = sessionsState(team)

            state.launch(1, mode)
            await { id in opened }

            assertThat(state.sessionTeams[id]).isSameAs(team)
            verify(backend.launcher).launch(1, mode, team)
        }
    }

    @Test
    fun `continue reuses the team the project last started with instead of the default`() {
        val chosen = Team(8, "Chosen", emptyList())
        doReturn(session("ccm-first")).`when`(backend.launcher).launchTeam(1, chosen, "task")
        doReturn(session("ccm-second")).`when`(backend.launcher).launch(1, LaunchMode.CONTINUE, chosen)
        val state = sessionsState(team)

        state.launchTask(1, chosen, "task") {}
        await { "ccm-first" in opened }
        await { !state.launching }
        state.launch(1, LaunchMode.CONTINUE)
        await { "ccm-second" in opened }

        assertThat(state.sessionTeams["ccm-second"]).isSameAs(chosen)
    }

    @Test
    fun `launchIn starts the session in the directory and records it for the session`() {
        val dir = java.nio.file.Path.of("repo-task-x")
        doReturn(session("ccm-in")).`when`(backend.launcher).launchIn(1, dir, LaunchMode.NEW, null)
        val state = sessionsState(null)

        state.launchIn(1, dir, LaunchMode.NEW)
        await { "ccm-in" in opened }

        assertThat(state.sessionDirectories["ccm-in"]).isEqualTo(dir)
        state.close(1, "ccm-in")
        assertThat(state.sessionDirectories).isEmpty()
    }

    @Test
    fun `launchTaskIn reports a failed start through onNotStarted and records nothing`() {
        val dir = java.nio.file.Path.of("repo-task-x")
        org.mockito.Mockito.doThrow(IllegalStateException("boom")).`when`(backend.launcher).launchTaskIn(1, dir, team, "task")
        val failed = CopyOnWriteArrayList<String>()
        val state = SessionsState(
            backend, scope, { error("not used") }, { null }, { null },
            onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { failed += it },
        )
        val notStarted = CopyOnWriteArrayList<String>()

        state.launchTaskIn(1, dir, team, "task", onNotStarted = { notStarted += it }) { error("started") }
        await { notStarted.isNotEmpty() }

        assertThat(state.sessionDirectories).isEmpty()
        assertThat(notStarted).containsExactly("boom")
        assertThat(failed).isEmpty()
    }

    @Test
    fun `resume records the folder the launcher started in, not the project path`() {
        val worktree = java.nio.file.Path.of("repo-task-x")
        val claudeSessionId = "123e4567-e89b-12d3-a456-426614174000"
        doReturn(LaunchedSession("ccm-resumed", 1, "claude", mock(PtyProcess::class.java), worktree)).`when`(backend.launcher).resume(1, claudeSessionId, worktree, null)
        val state = SessionsState(
            backend, scope, { LiveSessions() }, { null }, { null },
            onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { error(it) },
            projectPath = { java.nio.file.Path.of("repo") },
        )

        state.resume(1, claudeSessionId, worktree)
        await { "ccm-resumed" in opened }

        assertThat(state.sessionDirectories["ccm-resumed"]).isEqualTo(worktree)
    }

    @Test
    fun `a session that ended by itself does not count as live work in its folder`() {
        val dir = java.nio.file.Path.of("repo-task-x")
        doReturn(session("ccm-in")).`when`(backend.launcher).launchIn(1, dir, LaunchMode.NEW, null)
        var status = AgentStatus.IDLE
        val snapshot = mock(SessionSnapshot::class.java)
        `when`(snapshot.ccmSessionId).thenReturn("ccm-in")
        `when`(snapshot.projectId).thenReturn(1L)
        `when`(snapshot.status).thenAnswer { status }
        val state = SessionsState(
            backend, scope, { LiveSessions(listOf(snapshot)) }, { null }, { null },
            onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { error(it) },
        )
        state.launchIn(1, dir, LaunchMode.NEW)
        await { "ccm-in" in opened }

        assertThat(state.hasLiveIn(dir)).isTrue()
        status = AgentStatus.OFFLINE
        assertThat(state.hasLiveIn(dir)).isFalse()
        assertThat(state.hasLiveIn(java.nio.file.Path.of("elsewhere"))).isFalse()
    }

    @Test
    fun `a launch without a directory records the project folder when the project path is known`() {
        doReturn(session("ccm-plain")).`when`(backend.launcher).launch(1, LaunchMode.NEW)
        val state = SessionsState(
            backend, scope, { error("not used") }, { null }, { null },
            onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { error(it) },
            projectPath = { java.nio.file.Path.of("project") },
        )

        state.launch(1, LaunchMode.NEW)
        await { "ccm-plain" in opened }

        assertThat(state.sessionDirectories["ccm-plain"]).isEqualTo(java.nio.file.Path.of("project"))
    }

    @Test
    fun `launch without any team uses the plain launch and records no team`() {
        doReturn(session("ccm-plain")).`when`(backend.launcher).launch(1, LaunchMode.CONTINUE)
        val state = sessionsState(null)

        state.launch(1, LaunchMode.CONTINUE)
        await { "ccm-plain" in opened }

        assertThat(state.sessionTeams).isEmpty()
        verify(backend.launcher).launch(1, LaunchMode.CONTINUE)
    }

    @Test
    fun `task without a team starts a plain session with no team recorded`() {
        doReturn(session("ccm-task")).`when`(backend.launcher).launchTask(1, "task")
        val state = sessionsState(team)

        state.launchTask(1, null, "task") {}
        await { "ccm-task" in opened }

        assertThat(state.sessionTeams).isEmpty()
        verify(backend.launcher).launchTask(1, "task")
    }

    @Test
    fun `continue after a task without a team stays team-less instead of using the default`() {
        doReturn(session("ccm-task")).`when`(backend.launcher).launchTask(1, "task")
        doReturn(session("ccm-continue")).`when`(backend.launcher).launch(1, LaunchMode.CONTINUE)
        val state = sessionsState(team)

        state.launchTask(1, null, "task") {}
        await { "ccm-task" in opened }
        await { !state.launching }
        state.launch(1, LaunchMode.CONTINUE)
        await { "ccm-continue" in opened }

        assertThat(state.sessionTeams).isEmpty()
        verify(backend.launcher).launch(1, LaunchMode.CONTINUE)
    }

    @Test
    fun `quick action send with Enter sends no Ctrl+C and no text while a draft exists`() {
        val sent = CopyOnWriteArrayList<String>()
        val errors = CopyOnWriteArrayList<String>()
        val draftTerminal = object : TerminalPort by terminal {
            override fun send(ccmSessionId: String, text: String) { sent += text }
            override fun draftMayExist(ccmSessionId: String) = true
        }
        val idle = com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot(
            "s1", 1, "claude", com.github.krpkbhdn.ccmanager.claude.session.AgentStatus.IDLE, "", "default", null, null,
            java.time.Instant.EPOCH, java.time.Instant.EPOCH, emptyList(), 0, null,
        )
        val state = SessionsState(
            PreviewWorld().backend(draftTerminal), scope, { LiveSessions(listOf(idle)) }, { null }, { null },
            onOpened = { _, _ -> }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { errors += it },
        )

        state.send("s1", "/compact" + Keys.ENTER)
        await { errors.isNotEmpty() }

        assertThat(sent).isEmpty()
        assertThat(errors).singleElement().asString().contains("unsent text")
    }

    @Test
    fun `send calls onSent after the command is typed and submitted`() {
        val sent = CopyOnWriteArrayList<String>()
        val sentCallbacks = java.util.concurrent.atomic.AtomicInteger()
        val recordingTerminal = object : TerminalPort by terminal {
            override fun send(ccmSessionId: String, text: String) { sent += text }
        }
        val idle = com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot(
            "s1", 1, "claude", com.github.krpkbhdn.ccmanager.claude.session.AgentStatus.IDLE, "", "default", null, null,
            java.time.Instant.EPOCH, java.time.Instant.EPOCH, emptyList(), 0, null,
        )
        val state = SessionsState(
            PreviewWorld().backend(recordingTerminal), scope, { LiveSessions(listOf(idle)) }, { null }, { null },
            onOpened = { _, _ -> }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { error(it) },
        )

        state.send("s1", "/compact" + Keys.ENTER) { sentCallbacks.incrementAndGet() }
        await { sentCallbacks.get() == 1 }

        assertThat(sent).containsExactly("/compact", Keys.ENTER)
    }
}
