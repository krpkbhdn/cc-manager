package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.GitStatus.Kind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ChangeSectionsTest {
    private fun change(path: String, staged: Kind, unstaged: Kind) = GitStatus.Change(path, null, staged, unstaged)

    private val changes = listOf(
        change("b.txt", Kind.MODIFIED, Kind.MODIFIED),
        change("a.txt", Kind.ADDED, Kind.NONE),
        change("c.txt", Kind.NONE, Kind.UNTRACKED),
        change("d.txt", Kind.UNMERGED, Kind.UNMERGED),
        change("e.txt", Kind.NONE, Kind.DELETED),
    )

    @Test
    fun `a file edited on both sides shows in staged and unstaged`() {
        val sections = changeSections(changes)

        assertThat(sections.staged.map { it.path() }).containsExactly("a.txt", "b.txt")
        assertThat(sections.unstaged.map { it.path() }).containsExactly("b.txt", "c.txt", "e.txt")
    }

    @Test
    fun `conflicted files live only in their own section`() {
        val sections = changeSections(changes)

        assertThat(sections.conflicts.map { it.path() }).containsExactly("d.txt")
        assertThat(sections.staged.map { it.path() } + sections.unstaged.map { it.path() }).doesNotContain("d.txt")
    }

    @Test
    fun `dirty count counts each path once`() {
        assertThat(changeSections(changes).dirtyCount).isEqualTo(5)
        assertThat(changeSections(emptyList()).isClean).isTrue()
    }

    @Test
    fun `kind shown depends on the list`() {
        val both = change("b.txt", Kind.ADDED, Kind.MODIFIED)

        assertThat(both.kindIn(staged = true)).isEqualTo(Kind.ADDED)
        assertThat(both.kindIn(staged = false)).isEqualTo(Kind.MODIFIED)
        assertThat(kindLetter(Kind.UNTRACKED)).isEqualTo("U")
        assertThat(kindLabel(Kind.UNTRACKED)).isEqualTo("New file")
    }
}
