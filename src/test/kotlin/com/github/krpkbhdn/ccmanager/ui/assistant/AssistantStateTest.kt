package com.github.krpkbhdn.ccmanager.ui.assistant

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.LiveSessions
import com.github.krpkbhdn.ccmanager.ui.state.TerminalPort
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val TRUST_SCREEN = " Do you trust the files in this folder?\n > 1. Yes, I trust this folder\n   2. No, exit"

class AssistantStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sent = CopyOnWriteArrayList<String>()
    private val launches = AtomicInteger()
    private val shown = AtomicInteger()
    private val confirms = CopyOnWriteArrayList<ConfirmRequest>()
    @Volatile private var screen = ""
    @Volatile private var live = LiveSessions()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) { sent += text }
        override fun close(ccmSessionId: String) = Unit
        override fun screenText(ccmSessionId: String) = screen

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val state = AssistantState(
        scope, terminal, { live }, Path.of("assistant"), { launches.incrementAndGet() }, { shown.incrementAndGet() }, { confirms += it },
        pollMillis = 10, watchMillis = 400,
    )

    @AfterEach
    fun stop() = scope.cancel()

    private fun session(claudeId: String?) = SessionSnapshot(
        "ccm-a", ClaudeLauncher.ASSISTANT_PROJECT_ID, claudeId, AgentStatus.IDLE, null, null, null, null, Instant.now(), Instant.now(), emptyList(), 0, null,
    )

    @Test
    fun `trust dialog is recognised loosely on a realistic screen and not on an ordinary one`() {
        assertThat(isTrustDialog(TRUST_SCREEN)).isTrue()
        assertThat(isTrustDialog("> Try \"refactor the build\"")).isFalse()
    }

    @Test
    fun `yes on the trust question sends down and enter to the terminal`() {
        screen = TRUST_SCREEN

        state.start()
        live = LiveSessions(listOf(session(null)))
        await { confirms.isNotEmpty() }
        assertThat(confirms.single().message).contains("Trust the assistant folder")
        confirms.single().onConfirm()

        assertThat(sent).containsExactly("\u001b[B\r")
    }

    @Test
    fun `watching stops without asking once the session has a claude id`() {
        screen = TRUST_SCREEN

        state.start()
        live = LiveSessions(listOf(session("claude-1")))
        Thread.sleep(100)

        assertThat(confirms).isEmpty()
    }

    @Test
    fun `start focuses the live session instead of launching a second`() {
        live = LiveSessions(listOf(session("claude-1")))

        state.start()

        assertThat(launches.get()).isZero()
        assertThat(shown.get()).isEqualTo(1)
        assertThat(state.liveSessionId).isEqualTo("ccm-a")
    }

    @Test
    fun `start launches when no assistant session is live`() {
        state.start()

        assertThat(launches.get()).isEqualTo(1)
        assertThat(state.liveSessionId).isNull()
    }
}
