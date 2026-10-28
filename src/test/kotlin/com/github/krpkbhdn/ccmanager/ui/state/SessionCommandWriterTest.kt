package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SessionCommandWriterTest {
    private val events = mutableListOf<String>()
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) { events += "$ccmSessionId:" + text.replace("\r", "<CR>").replace("\u001b", "<ESC>") }
        override fun close(ccmSessionId: String) = Unit

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val writer = SessionCommandWriter(terminal) { events += "pause:$it" }

    @Test
    fun inputIsClearedThenCommandTextAndEnterAreSeparateWritesWithPauses() = runBlocking {
        assertThat(writer.run("s1", "/model sonnet")).isTrue()

        assertThat(events).containsExactly("s1:\u0003", "pause:700", "s1:/model sonnet", "pause:700", "s1:<CR>")
    }

    @Test
    fun aSecondRunIsIgnoredWhileTheFirstIsStillBeingSent() = runBlocking {
        lateinit var reentrant: SessionCommandWriter
        var secondRan: Boolean? = null
        reentrant = SessionCommandWriter(terminal) { secondRan = runBlocking { reentrant.run("s1", "/x") } }
        reentrant.run("s1", "/first")

        assertThat(secondRan).isFalse()
        assertThat(events).filteredOn { it.contains("/x") }.isEmpty()
    }

    @Test
    fun nothingIsTypedWhenTheUserPressedCtrlCASecondAgo() = runBlocking {
        val recentCtrlC = object : TerminalPort by terminal {
            override fun ctrlCWithin(ccmSessionId: String, windowMillis: Long) = true
        }

        assertThat(SessionCommandWriter(recentCtrlC) { }.run("s1", "/x")).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun nothingIsTypedWhenADraftAppearsBeforeTheWrite() = runBlocking {
        assertThat(writer.run("s1", "queued", clearDraft = false) { true }).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun nothingIsTypedRightAfterTheUserPressedEnter() = runBlocking {
        val recentEnter = object : TerminalPort by terminal {
            override fun enterWithin(ccmSessionId: String, windowMillis: Long) = true
        }

        assertThat(SessionCommandWriter(recentEnter) { }.run("s1", "/x")).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun onlyAnIdleSessionMayReceiveACommand() {
        assertThat(commandBlockedReason(snapshot(AgentStatus.IDLE, "c1"))).isNull()
        listOf(AgentStatus.WORKING, AgentStatus.NEEDS_ACTION, AgentStatus.OFFLINE)
            .forEach { assertThat(commandBlockedReason(snapshot(it, "c1"))).isNotNull() }
        assertThat(commandBlockedReason(null)).isNotNull()
    }

    @Test
    fun aSessionBeforeSessionStartIsBlocked() {
        assertThat(commandBlockedReason(snapshot(AgentStatus.IDLE, null))).isEqualTo(STARTING_MESSAGE)
    }

    @Test
    fun anAttachedSessionIsNotHeldBackForSessionStart() {
        assertThat(commandBlockedReason(snapshot(AgentStatus.IDLE, null), attached = true)).isNull()
        assertThat(commandBlockedReason(snapshot(AgentStatus.WORKING, null), attached = true)).isNotNull()
    }

    private fun snapshot(status: AgentStatus, claudeSessionId: String?) =
        SessionSnapshot("s1", 1, claudeSessionId, status, "", "default", null, null, Instant.EPOCH, Instant.EPOCH, emptyList(), 0, null)

    @Test
    fun modelPickerIsAltPAsEscapeP() {
        writer.openModelPicker("s1")

        assertThat(events).containsExactly("s1:<ESC>p")
    }
}
