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
import kotlinx.coroutines.job
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

private const val POLLS_BEFORE_ASSERTING = 3

private const val TRUST_SCREEN = " Do you trust the files in this folder?\n > 1. Yes, I trust this folder\n   2. No, exit"

private const val TRUST_SCREEN_NO_DEFAULT = " Do you trust the files in this folder?\n   1. Yes, I trust this folder\n > 2. No, exit"

class AssistantStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val sent = CopyOnWriteArrayList<String>()
    private val launches = AtomicInteger()
    private val shown = AtomicInteger()
    private val queued = CopyOnWriteArrayList<Pair<String, String>>()
    private val confirms = CopyOnWriteArrayList<ConfirmRequest>()
    private val errors = CopyOnWriteArrayList<String>()
    @Volatile private var screen = ""
    @Volatile private var live = LiveSessions()
    private val liveReads = AtomicInteger()
    private val screenReads = AtomicInteger()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) { sent += text }
        override fun close(ccmSessionId: String) = Unit
        override fun screenText(ccmSessionId: String): String { screenReads.incrementAndGet(); return screen }

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val state = AssistantState(
        scope, terminal, { liveReads.incrementAndGet(); live }, Path.of("assistant"), { launches.incrementAndGet() }, { shown.incrementAndGet() }, { id, text -> queued += id to text }, { confirms += it }, { title, _ -> errors += title },
        pollMillis = 10, watchMillis = 400, answerMillis = 400,
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
    fun `trust keys are enter alone only when the cursor is on the yes line`() {
        assertThat(trustYesKeys(TRUST_SCREEN)).isEqualTo("\r")
        assertThat(trustYesKeys(TRUST_SCREEN_NO_DEFAULT)).isEqualTo("\u001b[B\r")
        assertThat(trustYesKeys(" Do you trust the files in this folder?\n   1. Yes, I trust this folder\n   2. No, exit")).isEqualTo("\u001b[B\r")
    }

    @Test
    fun `yes sends down and enter when the dialog defaults to no`() {
        screen = TRUST_SCREEN_NO_DEFAULT

        state.start()
        live = LiveSessions(listOf(session(null)))
        await { confirms.isNotEmpty() }
        confirms.single().onConfirm()

        assertThat(sent).containsExactly("\u001b[B\r")
    }

    @Test
    fun `queued request is reported when the claude id never appears`() {
        state.request("stuck")
        live = LiveSessions(listOf(session(null)))
        await { errors.isNotEmpty() }

        assertThat(queued).isEmpty()
    }

    @Test
    fun `yes on the trust question sends only enter to the terminal`() {
        screen = TRUST_SCREEN

        state.start()
        live = LiveSessions(listOf(session(null)))
        await { confirms.isNotEmpty() }
        assertThat(confirms.single().message).contains("Trust the assistant folder")
        confirms.single().onConfirm()

        assertThat(sent).containsExactly("\r")
    }

    @Test
    fun `yes sends nothing when the trust dialog is already gone from the screen`() {
        screen = TRUST_SCREEN

        state.start()
        live = LiveSessions(listOf(session(null)))
        await { confirms.isNotEmpty() }
        screen = "> Try \"refactor the build\""
        confirms.single().onConfirm()

        assertThat(sent).isEmpty()
    }

    @Test
    fun `a queued request echoing trust and folder is not mistaken for the dialog`() {
        assertThat(isTrustDialog("> please trust the folder layout")).isFalse()
    }

    @Test
    fun `trust dialog appearing after the watch time is still offered`() {
        state.start()
        live = LiveSessions(listOf(session(null)))
        val reads = screenReads.get()
        await { screenReads.get() >= reads + POLLS_BEFORE_ASSERTING }
        screen = TRUST_SCREEN
        await { confirms.isNotEmpty() }

        assertThat(confirms).hasSize(1)
    }

    @Test
    fun `watching stops without asking once the session has a claude id`() {
        screen = TRUST_SCREEN

        state.start()
        live = LiveSessions(listOf(session("claude-1")))
        await { scope.coroutineContext.job.children.none { it.isActive } }

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

    @Test
    fun `request queues the text for the live assistant`() {
        live = LiveSessions(listOf(session("claude-1")))

        state.request("do it")

        assertThat(queued).containsExactly("ccm-a" to "do it")
        assertThat(launches.get()).isZero()
    }

    @Test
    fun `request launches once and queues when the session appears`() {
        state.request("first")
        state.request("second")
        live = LiveSessions(listOf(session(null)))
        val reads = liveReads.get()
        await { liveReads.get() >= reads + POLLS_BEFORE_ASSERTING }
        assertThat(queued).isEmpty()
        live = LiveSessions(listOf(session("claude-1")))
        await { queued.size == 2 }

        assertThat(launches.get()).isEqualTo(1)
        assertThat(queued).containsExactly("ccm-a" to "first", "ccm-a" to "second")
    }

    @Test
    fun `request is reported, not dropped silently, when the session never appears`() {
        state.request("lost")
        await { errors.isNotEmpty() }

        assertThat(queued).isEmpty()
    }

    @Test
    fun `blank request is ignored`() {
        state.request("  ")

        assertThat(launches.get()).isZero()
        assertThat(queued).isEmpty()
    }
}
