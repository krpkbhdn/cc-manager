package com.github.krpkbhdn.ccmanager.ui.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WebModelTest {
    private fun line(text: String, level: ConsoleLevel = ConsoleLevel.LOG, time: Long = 0) = ConsoleLine(level, text, "a.js", 1, time)

    @Test
    fun consoleBufferDropsOldestBeyondLimit() {
        val buffer = ConsoleBuffer(3)
        (1..5).forEach { buffer.add(line("m$it")) }
        assertThat(buffer.lines().map { it.text }).containsExactly("m3", "m4", "m5")
    }

    @Test
    fun consoleBufferCollapsesConsecutiveDuplicates() {
        val buffer = ConsoleBuffer()
        buffer.add(line("x")); buffer.add(line("x", time = 5)); buffer.add(line("y")); buffer.add(line("x"))
        assertThat(buffer.lines().map { it.text to it.repeat }).containsExactly("x" to 2, "y" to 1, "x" to 1)
    }

    @Test
    fun consoleBufferFiltersByLevel() {
        val buffer = ConsoleBuffer()
        buffer.add(line("a")); buffer.add(line("b", ConsoleLevel.ERROR))
        assertThat(buffer.lines(setOf(ConsoleLevel.ERROR)).map { it.text }).containsExactly("b")
    }

    @Test
    fun styleDiffIgnoresNoOpsAndReportsChanges() {
        val computed = mapOf("color" to "red", "font-size" to "12px")
        val changes = StyleDiff.changes(computed, mapOf("color" to "red", "font-size" to "14px", "gap" to "4px"))
        assertThat(changes).containsExactly(StyleChange("font-size", "12px", "14px"), StyleChange("gap", "", "4px"))
    }

    @Test
    fun styleDiffExpandsPaddingShorthand() {
        val computed = mapOf("padding-top" to "8px", "padding-right" to "8px", "padding-bottom" to "8px", "padding-left" to "8px")
        assertThat(StyleDiff.changes(computed, mapOf("padding" to "8px 16px")))
            .containsExactly(StyleChange("padding-right", "8px", "16px"), StyleChange("padding-left", "8px", "16px"))
    }

    @Test
    fun shorthandParsesAndFormats() {
        assertThat(StyleDiff.parseSides("8px 16px")).isEqualTo(CssSides("8px", "16px", "8px", "16px"))
        assertThat(StyleDiff.parseSides("1px 2px 3px")).isEqualTo(CssSides("1px", "2px", "3px", "2px"))
        assertThat(StyleDiff.parseSides("1px 2px 3px 4px")!!.left).isEqualTo("4px")
        assertThat(StyleDiff.parseSides("1 2 3 4 5")).isNull()
        assertThat(StyleDiff.parseSides("  ")).isNull()
        assertThat(StyleDiff.formatSides(CssSides("8px", "16px", "8px", "16px"))).isEqualTo("8px 16px")
        assertThat(StyleDiff.formatSides(CssSides("1px", "1px", "1px", "1px"))).isEqualTo("1px")
        assertThat(StyleDiff.formatSides(CssSides("1px", "2px", "3px", "2px"))).isEqualTo("1px 2px 3px")
        assertThat(StyleDiff.formatSides(CssSides("1px", "2px", "3px", "4px"))).isEqualTo("1px 2px 3px 4px")
    }

    @Test
    fun togglesFlipOneFlagEach() {
        val t = InspectorToggles()
        assertThat(t.togglePick().pick).isTrue()
        assertThat(t.toggleShowAll().showAll).isTrue()
        assertThat(t.toggleShowBox().showBox).isFalse()
        assertThat(t.toggleShowLabels().showLabels).isFalse()
        assertThat(t.togglePick().togglePick()).isEqualTo(t)
    }

    private fun target(tag: String, id: String = "", classes: List<String> = emptyList(), hint: SourceHint? = null) =
        ElementTarget("main > $tag", "", tag, id, classes, "", "", null, null, emptyMap(), null, hint, null, null)

    @Test
    fun targetTitleUsesIdClassOrSelectorTail() {
        assertThat(formatTargetTitle(target("button", classes = listOf("save")))).isEqualTo("button.save")
        assertThat(formatTargetTitle(target("div", id = "app"))).isEqualTo("div#app")
        assertThat(formatTargetTitle(target(""))).isEqualTo("")
        assertThat(formatTargetTitle(target("button", classes = listOf("save"), hint = SourceHint("Foo", "src/Foo.tsx", 42))))
            .isEqualTo("button.save  src/Foo.tsx:42 <Foo>")
    }
}
