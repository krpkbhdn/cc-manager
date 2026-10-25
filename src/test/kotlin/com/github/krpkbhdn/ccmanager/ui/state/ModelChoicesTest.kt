package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
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

    private fun session(status: AgentStatus, claudeSessionId: String? = "c1") =
        SessionSnapshot("s1", 1, claudeSessionId, status, "", "default", null, null, Instant.EPOCH, Instant.EPOCH, emptyList(), 0, null)

    @Test
    fun anAttachedSessionBeforeSessionStartIsNotBlocked() {
        assertThat((modelTarget(session(AgentStatus.IDLE, null), false, attached = true) as ModelTarget.Running).blockedReason).isNull()
    }

    @Test
    fun aSessionBeforeSessionStartIsBlocked() {
        assertThat((modelTarget(session(AgentStatus.IDLE, null), false) as ModelTarget.Running).blockedReason).isEqualTo(STARTING_MESSAGE)
    }

    @Test
    fun noSessionMeansOnlyTheNextLaunchDefaultChanges() {
        assertThat(modelTarget(null, false)).isEqualTo(ModelTarget.NextLaunch)
    }

    @Test
    fun aFreeSessionCanTakeCommands() {
        assertThat(modelTarget(session(AgentStatus.IDLE), false)).isEqualTo(ModelTarget.Running("s1", null))
    }

    @Test
    fun busyWaitingAndApplyingSessionsAreBlockedWithAReason() {
        assertThat((modelTarget(session(AgentStatus.WORKING), false) as ModelTarget.Running).blockedReason).contains("working")
        assertThat((modelTarget(session(AgentStatus.NEEDS_ACTION), false) as ModelTarget.Running).blockedReason).contains("waiting")
        assertThat((modelTarget(session(AgentStatus.IDLE), true) as ModelTarget.Running).blockedReason).contains("Applying")
    }

    @Test
    fun onlyKnownEffortLevelsSelectASegment() {
        assertThat(knownEffort("xhigh")).isEqualTo("xhigh")
        assertThat(knownEffort("auto")).isNull()
        assertThat(knownEffort(null)).isNull()
    }

    @Test
    fun slashCommandsUseAliasesIdsAndTheDefaultWords() {
        assertThat(modelCommand("sonnet")).isEqualTo("/model sonnet")
        assertThat(modelCommand("opus[1m]")).isEqualTo("/model opus[1m]")
        assertThat(modelCommand(null)).isEqualTo("/model default")
        assertThat(effortCommand("high")).isEqualTo("/effort high")
        assertThat(effortCommand(null)).isEqualTo("/effort auto")
    }

    @Test
    fun commandsRefuseNamesAndLevelsThatCouldInjectInput() {
        assertThatThrownBy { modelCommand("x\r/exit") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { effortCommand("ultra") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun reportedDisplayNamesMatchAliasesAndIds() {
        assertThat(modelReported("Sonnet 5.5", "sonnet")).isTrue()
        assertThat(modelReported("Sonnet 5.5", "claude-sonnet-5-5")).isTrue()
        assertThat(modelReported("Haiku 5.5", "sonnet")).isFalse()
        assertThat(modelReported(null, "sonnet")).isFalse()
        assertThat(modelReported("Sonnet 5.5", null)).isFalse()
    }
}
