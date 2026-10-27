package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.Worktree
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitWorktreeRulesTest {
    private fun worktree(branch: String, head: String?, dirty: Boolean = false) =
        Worktree(Path.of("/r"), branch, head, false, branch.isEmpty(), false, false, dirty, false)

    @Test
    fun `title is the branch name`() {
        assertThat(worktreeTitle(worktree("feature/x", "abcdef1234"))).isEqualTo("feature/x")
    }

    @Test
    fun `title of a detached worktree shows the short hash`() {
        assertThat(worktreeTitle(worktree("", "abcdef1234567"))).isEqualTo("detached abcdef1")
        assertThat(worktreeTitle(worktree("  ", null))).isEqualTo("detached ")
    }

    @Test
    fun `only a dirty worktree needs force to be removed`() {
        assertThat(removalNeedsForce(worktree("a", "1", dirty = true))).isTrue()
        assertThat(removalNeedsForce(worktree("a", "1", dirty = false))).isFalse()
    }

    @Test
    fun `refilled path follows the suggestion until the user edits it`() {
        assertThat(refilledPath("", "/p/a", "/p/b")).isEqualTo("/p/b")
        assertThat(refilledPath("  ", "/p/a", "/p/b")).isEqualTo("/p/b")
        assertThat(refilledPath("/p/a", "/p/a", "/p/b")).isEqualTo("/p/b")
        assertThat(refilledPath("/mine", "/p/a", "/p/b")).isEqualTo("/mine")
    }

    @Test
    fun `default path is a sibling named after repo and branch`() {
        val top = Path.of("/code/work")

        assertThat(defaultWorktreePath(top, "feature/x")).isEqualTo(Path.of("/code/work-wt-feature-x").toString())
    }

    @Test
    fun `default path is empty when the root has no name or the branch is no valid path`() {
        assertThat(defaultWorktreePath(Path.of("/").root, "x")).isEmpty()
        if (System.getProperty("os.name").startsWith("Windows")) {
            assertThat(defaultWorktreePath(Path.of("C:/code/work"), "a|b")).isEmpty()
        }
    }
}
