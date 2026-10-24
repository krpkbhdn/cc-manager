package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.project.ModelSelection
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
}
