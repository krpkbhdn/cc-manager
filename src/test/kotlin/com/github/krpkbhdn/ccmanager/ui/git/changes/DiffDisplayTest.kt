package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewComment
import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewStatus
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class DiffDisplayTest {
    private fun comment(status: ReviewStatus) = ReviewComment("id", "a.txt", 1..1, false, emptyList(), emptyList(), "t", status)

    @Test
    fun `a stale comment shows even when an open one covers the same line`() {
        assertThat(displayStatus(listOf(comment(ReviewStatus.OPEN), comment(ReviewStatus.STALE)))).isEqualTo(ReviewStatus.STALE)
        assertThat(displayStatus(listOf(comment(ReviewStatus.SENT), comment(ReviewStatus.OPEN)))).isEqualTo(ReviewStatus.SENT)
        assertThat(displayStatus(emptyList())).isNull()
    }

    @Test
    fun `a new or removed file without lines is empty, other files only changed mode or name`() {
        assertThat(emptyDiffNotice(ChangeType.UNTRACKED).first).isEqualTo("Empty file")
        assertThat(emptyDiffNotice(ChangeType.ADDED).first).isEqualTo("Empty file")
        assertThat(emptyDiffNotice(ChangeType.MODIFIED).first).isEqualTo("No textual changes")
    }

    @Test
    fun `a huge line is cut for display with a marker and the highlight stays inside it`() {
        val shown = highlighted("x".repeat(MAX_SHOWN_LINE_LENGTH * 5), 0..MAX_SHOWN_LINE_LENGTH * 4, Color.Red)

        assertThat(shown.text).hasSize(MAX_SHOWN_LINE_LENGTH + 1).endsWith("…")
        assertThat(shown.spanStyles.single().end).isEqualTo(shown.text.length)
    }
}
