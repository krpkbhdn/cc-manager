package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiffRowsTest {
    private fun line(kind: GitDiff.LineKind, text: String) = GitDiff.Line(kind, text, null, null)


    @Test
    fun `changed middle skips the shared prefix and suffix`() {
        assertThat(changedMiddle("return new ArrayList<>(x);", "return List.of(x);")).isEqualTo(7 until 22)
        assertThat(changedMiddle("same", "same")).isNull()
    }

    @Test
    fun `a pure insertion has an empty range on the old side`() {
        assertThat(changedMiddle("ab", "abc")).isNull()
        assertThat(changedMiddle("abc", "ab")).isEqualTo(2 until 3)
    }

    @Test
    fun `removed and added runs are paired line by line`() {
        val rows = diffRows(
            listOf(
                line(GitDiff.LineKind.CONTEXT, "ctx"),
                line(GitDiff.LineKind.REMOVED, "int a = 1;"),
                line(GitDiff.LineKind.ADDED, "int a = 2;"),
            ),
        )
        assertThat(rows[0].highlight).isNull()
        assertThat(rows[1].highlight).isEqualTo(8 until 9)
        assertThat(rows[2].highlight).isEqualTo(8 until 9)
    }

    @Test
    fun `unpaired lines carry no highlight`() {
        val rows = diffRows(listOf(line(GitDiff.LineKind.REMOVED, "a"), line(GitDiff.LineKind.REMOVED, "b"), line(GitDiff.LineKind.ADDED, "c")))
        assertThat(rows[1].highlight).isNull()
    }

    @Test
    fun `change bar splits five blocks by proportion and keeps both signs visible`() {
        assertThat(changeBar(0, 0)).isEqualTo(Triple(0, 0, 5))
        assertThat(changeBar(10, 0)).isEqualTo(Triple(5, 0, 0))
        assertThat(changeBar(1, 99)).isEqualTo(Triple(1, 4, 0))
        assertThat(changeBar(99, 1)).isEqualTo(Triple(4, 1, 0))
        assertThat(changeBar(6, 3)).isEqualTo(Triple(3, 2, 0))
    }
}
