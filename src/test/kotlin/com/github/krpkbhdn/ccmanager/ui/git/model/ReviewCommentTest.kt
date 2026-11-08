package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.ui.state.DiffRow
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ReviewCommentTest {
    private val rows = listOf(
        DiffRow(Line(LineKind.HUNK, "@@", null, null)),
        DiffRow(Line(LineKind.REMOVED, "old", 10, null)),
        DiffRow(Line(LineKind.ADDED, "new", null, 10)),
        DiffRow(Line(LineKind.CONTEXT, "same", 11, 11)),
        DiffRow(Line(LineKind.REMOVED, "gone", 12, null)),
    )

    @Test
    fun `mixed selection uses new-side numbers and anchors on added and context text`() {
        val comment = reviewCommentOf("A.kt", rows, 0..3, "why")

        assertThat(comment.lines).isEqualTo(10..11)
        assertThat(comment.removedOnly).isFalse()
        assertThat(comment.snippet).containsExactly("-old", "+new", " same")
        assertThat(comment.anchor).containsExactly("new", "same")
        assertThat(comment.status).isEqualTo(ReviewStatus.OPEN)
    }

    @Test
    fun `removed-only selection uses old-side numbers and anchors on removed text`() {
        val comment = reviewCommentOf("A.kt", rows, 4..4, "why")

        assertThat(comment.removedOnly).isTrue()
        assertThat(comment.lines).isEqualTo(12..12)
        assertThat(comment.anchor).containsExactly("gone")
    }
}
