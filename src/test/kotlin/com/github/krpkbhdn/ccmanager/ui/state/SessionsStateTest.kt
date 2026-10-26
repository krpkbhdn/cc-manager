package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
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
    fun `launch without any team uses the plain launch and records no team`() {
        doReturn(session("ccm-plain")).`when`(backend.launcher).launch(1, LaunchMode.CONTINUE)
        val state = sessionsState(null)

        state.launch(1, LaunchMode.CONTINUE)
        await { "ccm-plain" in opened }

        assertThat(state.sessionTeams).isEmpty()
        verify(backend.launcher).launch(1, LaunchMode.CONTINUE)
    }
}
