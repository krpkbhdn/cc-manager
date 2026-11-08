package com.github.krpkbhdn.ccmanager.ui.git.model

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitReviewOpsTest {
    @TempDir
    lateinit var repo: Path

    private fun comment(vararg anchor: String, line: Int = 1) =
        ReviewComment("c", "a.txt", line..line + anchor.size - 1, false, anchor.map { "+$it" }, anchor.toList(), "note")

    @Test
    fun movedSnippetGetsNewLineNumbers() {
        Files.write(repo.resolve("a.txt"), listOf("x", "y", "target one", "target two", "z"))

        val range = fileHaystack(repo, "a.txt")!!.locate(comment("target one", "target two", line = 1))

        assertThat(range).isEqualTo(3..4)
    }

    @Test
    fun deletedSnippetIsNotFound() {
        Files.write(repo.resolve("a.txt"), listOf("x", "y"))

        assertThat(fileHaystack(repo, "a.txt")!!.locate(comment("target"))).isNull()
    }

    @Test
    fun missingFileHasNoHaystack() {
        assertThat(fileHaystack(repo, "gone.txt")).isNull()
    }

    @Test
    fun removedHaystackKeepsOldSideNumbers() {
        val haystack = ReviewHaystack(listOf("a", "b", "c"), listOf(4, 5, 9))
        val removed = comment("c", line = 9).copy(removedOnly = true)

        assertThat(haystack.locate(removed)).isEqualTo(9..9)
    }

    @Test
    fun staleResultMarksCommentStale() {
        val comments = ReviewComments()
        comments.add(comment("target").copy(status = ReviewStatus.SENT))

        comments.recheck(mapOf("c" to null))

        assertThat(comments.all.single().status).isEqualTo(ReviewStatus.STALE)
    }
}
