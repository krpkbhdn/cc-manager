package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ModelChoicesTest {
    @Test
    fun labelsUseThePresetNameOrTheRawId() {
        assertThat(selectionLabel(ModelSelection.DEFAULT)).isEqualTo("Default model")
        assertThat(selectionLabel(ModelSelection("opus", "high"))).isEqualTo("Opus · high")
        assertThat(selectionLabel(ModelSelection(null, "low"))).isEqualTo("Default model · low")
        assertThat(selectionLabel(ModelSelection("claude-opus-4-7", null))).isEqualTo("claude-opus-4-7")
    }

    @Test
    fun onlyNamesOutsideThePresetsAreCustom() {
        assertThat(isCustomModel(ModelSelection("sonnet[1m]", null))).isFalse()
        assertThat(isCustomModel(ModelSelection.DEFAULT)).isFalse()
        assertThat(isCustomModel(ModelSelection("claude-opus-4-7", null))).isTrue()
    }

    @Test
    fun effectiveLabelNeedsAtLeastOneFigure() {
        assertThat(effectiveModelLabel("Opus 4.7", "xhigh")).isEqualTo("Opus 4.7 · xhigh")
        assertThat(effectiveModelLabel("Opus 4.7", null)).isEqualTo("Opus 4.7")
        assertThat(effectiveModelLabel(null, null)).isNull()
    }

    private fun session(status: AgentStatus, claudeId: String? = "c1") =
        SessionSnapshot("s1", 1, claudeId, status, "", "default", null, null, Instant.EPOCH, Instant.EPOCH, emptyList(), 0, null)

    @Test
    fun noSessionMeansOnlyTheNextLaunchDefaultChanges() {
        assertThat(modelTarget(null, false)).isEqualTo(ModelTarget.NextLaunch)
    }

    @Test
    fun aFreeSessionWithAConversationCanSwitch() {
        assertThat(modelTarget(session(AgentStatus.IDLE), false)).isEqualTo(ModelTarget.Running("s1", "c1", null))
    }

    @Test
    fun busyWaitingUnstartedAndSwitchingSessionsAreBlockedWithAReason() {
        assertThat((modelTarget(session(AgentStatus.WORKING), false) as ModelTarget.Running).blockedReason).contains("working")
        assertThat((modelTarget(session(AgentStatus.NEEDS_ACTION), false) as ModelTarget.Running).blockedReason).contains("waiting")
        assertThat((modelTarget(session(AgentStatus.IDLE, claudeId = null), false) as ModelTarget.Running).blockedReason).contains("no conversation")
        assertThat((modelTarget(session(AgentStatus.IDLE), true) as ModelTarget.Running).blockedReason).contains("Switching")
    }

    @Test
    fun onlyKnownEffortLevelsSelectASegment() {
        assertThat(knownEffort("xhigh")).isEqualTo("xhigh")
        assertThat(knownEffort("auto")).isNull()
        assertThat(knownEffort(null)).isNull()
    }
}
