package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.InternalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentSnapshot
import com.github.krpkbhdn.ccmanager.claude.session.SubagentState
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(InternalComposeUiApi::class)
class SubagentRowsTest {
    private val start = Instant.parse("2026-01-01T00:00:00Z")
    private val actions = SubagentActions({}, {})

    private fun heightOf(state: SubagentState, activity: String, stale: Boolean = false): Int {
        val agent = SubagentSnapshot("a", "developer", activity, state, false, 3, false, "", "", start, start, null, stale)
        val session = SessionSnapshot("s", 1, null, AgentStatus.WORKING, null, null, null, null, start, start, listOf(agent), 0, null)
        var height = 0
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            AppTheme(dark = true) {
                Box(Modifier.width(260.dp).onGloballyPositioned { height = it.size.height }) {
                    SubagentList(session, null, { start }, false, actions)
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        scene.close()
        return height
    }

    @Test
    fun `active row height is the same for short, long and waiting activity`() {
        val short = heightOf(SubagentState.WORKING, "Bash: ls")
        val thinking = heightOf(SubagentState.WORKING, "")
        val long = heightOf(SubagentState.WORKING, "Bash: " + "very-long-command ".repeat(40))
        val waiting = heightOf(SubagentState.NEEDS_ACTION, "Bash: ls")
        val stale = heightOf(SubagentState.WORKING, "Bash: ls", stale = true)

        assertThat(short).isGreaterThan(0)
        assertThat(listOf(thinking, long, waiting, stale)).allMatch { it == short }
    }

    private fun sessionCardHeight(status: AgentStatus, activity: String?, title: String? = "Fix login"): Int {
        val session = SessionSnapshot("s", 1, null, status, activity, null, null, title, start, start, emptyList(), 0, null)
        val panelActions = AgentsPanelActions({}, {}, {}, {}, { _, _ -> }, {})
        var height = 0
        val scene = ImageComposeScene(300, 600, Density(1f)) {
            AppTheme(dark = true) {
                Box(Modifier.width(260.dp).onGloballyPositioned { height = it.size.height }) {
                    SessionCard(session, null, false, false, { start }, panelActions, Modifier)
                }
            }
        }
        repeat(3) { scene.render(it * 16_000_000L) }
        scene.close()
        return height
    }

    @Test
    fun `main session card height is the same for any activity or title`() {
        val short = sessionCardHeight(AgentStatus.WORKING, "Bash: ls")
        val none = sessionCardHeight(AgentStatus.WORKING, null)
        val long = sessionCardHeight(AgentStatus.WORKING, "Bash: " + "very-long-command ".repeat(40))
        val longTitle = sessionCardHeight(AgentStatus.WORKING, "Bash: ls", "Fix login ".repeat(40))
        val waiting = sessionCardHeight(AgentStatus.NEEDS_ACTION, "Waiting for your decision")
        val offline = sessionCardHeight(AgentStatus.OFFLINE, "Bash: ls")

        assertThat(short).isGreaterThan(0)
        assertThat(listOf(none, long, longTitle, waiting, offline)).allMatch { it == short }
    }
}
