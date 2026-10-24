package com.github.krpkbhdn.ccmanager.ui.teams

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class ToolSelectionTest {
    @Test
    fun nothingStoredMeansInheritAllWithEveryToolTicked() {
        val selection = ToolSelection.parse(null)

        assertThat(selection.inheritsAll).isTrue()
        assertThat(builtInTools).allMatch(selection::allows)
        assertThat(selection.activePreset).isEqualTo(ToolPreset.INHERIT_ALL)
        assertThat(selection.summary).isEqualTo("All tools")
        assertThat(selection.serialize()).isNull()
    }

    @Test
    fun unTickingFromInheritRestrictsToTheOtherBuiltIns() {
        val selection = ToolSelection.INHERIT_ALL.toggle("Bash")

        assertThat(selection.allows("Bash")).isFalse()
        assertThat(selection.allows("Read")).isTrue()
        assertThat(selection.tools).hasSize(builtInTools.size - 1)
    }

    @Test
    fun presetsAreRecognisedWhateverTheStoredOrder() {
        assertThat(ToolSelection.parse("Grep, Glob, Read").activePreset).isEqualTo(ToolPreset.READ_ONLY)
        assertThat(ToolSelection.INHERIT_ALL.withPreset(ToolPreset.FULL_NO_WEB).activePreset).isEqualTo(ToolPreset.FULL_NO_WEB)
        assertThat(ToolSelection.parse("Read, Grep, Glob, Bash").activePreset).isNull()
    }

    @Test
    fun theSeededTesterMapsToFourTickedTools() {
        val selection = ToolSelection.parse("Read, Grep, Glob, Bash")

        assertThat(builtInTools.filter(selection::allows)).containsExactlyInAnyOrder("Read", "Grep", "Glob", "Bash")
        assertThat(selection.summary).isEqualTo("4 tools")
    }

    @Test
    fun togglingKeepsCatalogOrderAndCustomEntriesLast() {
        val selection = ToolSelection(listOf("Read")).addCustom("mcp__github__*").toggle("Bash").toggle("Edit")

        assertThat(selection.tools).containsExactly("Read", "Edit", "Bash", "mcp__github__*")
        assertThat(selection.custom).containsExactly("mcp__github__*")
        assertThat(selection.toggle("mcp__github__*").custom).isEmpty()
    }

    @Test
    fun customEntriesAreValidatedLikeTheBackend() {
        val selection = ToolSelection(listOf("Read"))

        assertThat(selection.addCustom(" mcp__db__query ").tools).containsExactly("Read", "mcp__db__query")
        assertThatThrownBy { selection.addCustom("Bash(git *)") }.hasMessageContaining("tool name")
        assertThatThrownBy { selection.addCustom("two words") }.isInstanceOf(IllegalArgumentException::class.java)
        assertThat(selection.addCustom("Read")).isEqualTo(selection)
    }

    @Test
    fun emptyAllowlistIsAnErrorNotSilentlyInherit() {
        val selection = ToolSelection(listOf("Read")).toggle("Read")

        assertThat(selection.error).contains("at least one tool")
        assertThat(selection.summary).isEqualTo("No tools")
    }

    @Test
    fun serializeAndParseRoundTrip() {
        val selection = ToolSelection(listOf("Read", "Bash", "mcp__x__*"))

        assertThat(ToolSelection.parse(selection.serialize())).isEqualTo(selection)
    }
}
