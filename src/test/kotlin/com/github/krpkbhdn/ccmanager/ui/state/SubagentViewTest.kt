package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentState
import java.time.Duration
import java.time.Instant
import org.junit.jupiter.api.Test
import org.assertj.core.api.Assertions.assertThat

class SubagentViewTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")

    private fun agent(id: String, state: SubagentState, activity: String = "", ended: Long? = null, calls: Int = 0, background: Boolean = false, stale: Boolean = false) =
        SubagentSnapshot(id, "developer", activity, state, background, calls, false, "", "", start, start, ended?.let { start.plusSeconds(it) }, stale)

    private fun session(vararg agents: SubagentSnapshot) =
        SessionSnapshot("s", 1, null, AgentStatus.WORKING, null, null, null, null, start, start, agents.toList(), 0, null)

    @Test
    fun activeAndFinishedAreSeparatedAndFinishedLatestFirst() {
        val s = session(agent("a", SubagentState.WORKING), agent("b", SubagentState.DONE, ended = 10), agent("c", SubagentState.FAILED, ended = 30))

        assertThat(s.activeSubagents().map { it.agentId }).isEqualTo(listOf("a"))
        assertThat(s.finishedSubagents().map { it.agentId }).isEqualTo(listOf("c", "b"))
    }

    @Test
    fun waitingAgentIsRedAndWorkingIsYellow() {
        assertThat(agent("a", SubagentState.NEEDS_ACTION).dotStatus()).isEqualTo(AgentStatus.NEEDS_ACTION)
        assertThat(agent("a", SubagentState.WORKING).dotStatus()).isEqualTo(AgentStatus.WORKING)
    }

    @Test
    fun lineShowsToolOrThinking() {
        assertThat(agent("a", SubagentState.WORKING).liveLine(start)).isEqualTo("Thinking…")
        assertThat(agent("a", SubagentState.WORKING, "Bash: ls").liveLine(start)).isEqualTo("Bash: ls")
        assertThat(agent("a", SubagentState.WORKING, "Bash: ls", stale = true).liveLine(start.plusSeconds(720))).isEqualTo("No activity for 12m 00s")
    }

    @Test
    fun elapsedRunsToNowWhileLiveAndStopsAtTheEndOtherwise() {
        assertThat(agent("a", SubagentState.WORKING).elapsed(start.plusSeconds(90))).isEqualTo(Duration.ofSeconds(90))
        assertThat(agent("a", SubagentState.DONE, ended = 20).elapsed(start.plusSeconds(500))).isEqualTo(Duration.ofSeconds(20))
    }

    @Test
    fun statsLinePluralisesAndMarksBackground() {
        assertThat(agent("a", SubagentState.WORKING, calls = 1).statsLine()).isEqualTo("1 tool call")
        assertThat(agent("a", SubagentState.WORKING, calls = 12, background = true).statsLine()).isEqualTo("12 tool calls · background")
    }
}
