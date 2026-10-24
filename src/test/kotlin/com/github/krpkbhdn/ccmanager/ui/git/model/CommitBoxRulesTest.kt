package com.github.krpkbhdn.ccmanager.ui.git.model

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitBoxRulesTest {
    @Test
    fun `commit needs a message and staged files`() {
        assertThat(canCommit("fix bug", stagedCount = 1, amend = false, busy = false)).isTrue()
        assertThat(canCommit("  ", stagedCount = 1, amend = false, busy = false)).isFalse()
        assertThat(canCommit("fix bug", stagedCount = 0, amend = false, busy = false)).isFalse()
    }

    @Test
    fun `an amend may reword without staged files`() {
        assertThat(canCommit("better words", stagedCount = 0, amend = true, busy = false)).isTrue()
    }

    @Test
    fun `no commit while busy or conflicted`() {
        assertThat(canCommit("msg", 2, amend = false, busy = true)).isFalse()
        assertThat(canCommit("msg", 2, amend = false, busy = false, conflicts = 1)).isFalse()
    }

    @Test
    fun `subject hint follows the 50 and 72 conventions`() {
        assertThat(subjectHint("a".repeat(50))).isEqualTo(SubjectHint(50, SubjectLevel.OK))
        assertThat(subjectHint("a".repeat(51)).level).isEqualTo(SubjectLevel.LONG)
        assertThat(subjectHint("a".repeat(72)).level).isEqualTo(SubjectLevel.LONG)
        assertThat(subjectHint("a".repeat(73)).level).isEqualTo(SubjectLevel.TOO_LONG)
    }

    @Test
    fun `only the first line counts as the subject`() {
        assertThat(subjectOf("short\n\n" + "b".repeat(200))).isEqualTo("short")
        assertThat(subjectHint("short\n" + "b".repeat(200)).length).isEqualTo(5)
        assertThat(subjectHint("").length).isZero()
    }

    @Test
    fun `unchecking amend restores what was typed before`() {
        val (prefilled, memory) = AmendMemory().onToggle(true, current = "my draft", lastMessage = "Previous commit")
        val (restored, cleared) = memory.onToggle(false, current = prefilled, lastMessage = "ignored")

        assertThat(prefilled).isEqualTo("Previous commit")
        assertThat(restored).isEqualTo("my draft")
        assertThat(cleared).isEqualTo(AmendMemory())
    }
}
