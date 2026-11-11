package com.github.krpkbhdn.ccmanager.ui.presets.apply

import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.preset.apply.ChangeAction
import com.github.krpkbhdn.ccmanager.preset.apply.OutcomeStatus
import com.github.krpkbhdn.ccmanager.ui.sampleOutcomes
import com.github.krpkbhdn.ccmanager.ui.samplePlan
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ApplyPresetModelTest {
    private val plan = samplePlan()

    @Test
    fun `only changes that write are selected by default`() {
        val selected = defaultSelection(plan)

        assertThat(selected).containsExactlyInAnyOrder(1L, 2L, 4L, 5L, 6L, 8L, 10L, 11L)
        assertThat(plan.filter { it.action() == ChangeAction.CONFLICT || it.action() == ChangeAction.PRESENT }.none { isSelectable(it) }).isTrue()
    }

    @Test
    fun `summary counts changes, present items and conflicts`() {
        assertThat(planSummary(plan).line()).isEqualTo("8 changes · 2 already there · 1 conflict")
    }

    @Test
    fun `summary says so when nothing would change`() {
        assertThat(planSummary(plan.filter { it.action() == ChangeAction.PRESENT }).line()).isEqualTo("No changes · 2 already there")
        assertThat(planSummary(emptyList()).line()).isEqualTo("No changes")
    }

    @Test
    fun `groups follow the review order and hold marketplaces with their plugins`() {
        val groups = groupChanges(plan)

        assertThat(groups.map { it.title }).containsExactly("Instructions", "Skills", "Plugins", "MCP servers", "Agents", "Files", "Settings")
        assertThat(groups.first { it.title == "Plugins" }.changes.map { it.kind() }.first()).isEqualTo(PresetComponentKind.MARKETPLACE)
    }

    @Test
    fun `apply button names the number of checked changes`() {
        assertThat(applyLabel(0)).isEqualTo("Apply")
        assertThat(applyLabel(1)).isEqualTo("Apply 1 change")
        assertThat(applyLabel(5)).isEqualTo("Apply 5 changes")
    }

    @Test
    fun `applying needs a checked change unless everything is already there`() {
        val present = plan.filter { it.action() == ChangeAction.PRESENT }

        assertThat(canApply(null, setOf(1L))).isFalse()
        assertThat(canApply(plan, emptySet())).isFalse()
        assertThat(canApply(plan, setOf(1L))).isTrue()
        assertThat(canApply(present, emptySet())).isTrue()
    }

    @Test
    fun `a preset newer than the applied revision is outdated`() {
        val preset = ProjectPreset(1, "General", "", "general", false, false, 3, emptyList())

        assertThat(presetStatus(preset, null)).isNull()
        assertThat(presetStatus(preset, 3)).isEqualTo(PresetStatus.APPLIED)
        assertThat(presetStatus(preset, 2)).isEqualTo(PresetStatus.OUTDATED)
    }

    @Test
    fun `outcome notes point to the page that finishes the job`() {
        val outcomes = sampleOutcomes().associateBy { it.status() }

        assertThat(outcomeNote(outcomes.getValue(OutcomeStatus.SIGN_IN_NEEDED))).isEqualTo("Sign in on the MCP servers page")
        assertThat(outcomeNote(outcomes.getValue(OutcomeStatus.NEEDS_CONFIRMATION))).isEqualTo("Confirm on the Plugins page")
        assertThat(outcomeNote(outcomes.getValue(OutcomeStatus.FAILED))).startsWith("Could not write")
    }

    @Test
    fun `result line counts the outcomes`() {
        assertThat(resultLine(sampleOutcomes())).isEqualTo("5 applied · 1 failed · 1 to confirm · 2 skipped")
        assertThat(resultLine(emptyList())).isEqualTo("Nothing was applied")
    }
}
