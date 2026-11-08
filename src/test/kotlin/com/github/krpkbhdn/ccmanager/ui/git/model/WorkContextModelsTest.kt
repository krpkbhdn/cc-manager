package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.ui.state.WorkContext
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WorkContextModelsTest {
    private val main = Path.of("repo")
    private val linked = Path.of("repo-task-x")

    private fun context(directory: Path, branch: String?, isMain: Boolean, active: Boolean = false) =
        WorkContext(directory, branch, isMain, false, null, null, active)

    @Test
    fun `main folder is titled with its branch and a worktree by its branch`() {
        assertThat(contextTitle(context(main, "main", true))).isEqualTo("main folder · main")
        assertThat(contextTitle(context(linked, "task/x", false))).isEqualTo("task/x")
    }

    @Test
    fun `a detached worktree is titled by its folder`() {
        assertThat(contextTitle(context(linked, null, false))).isEqualTo("repo-task-x")
        assertThat(contextTitle(context(main, null, true))).isEqualTo("main folder · detached")
    }

    @Test
    fun `selector label follows the active context and falls back to the main folder`() {
        val contexts = listOf(context(main, "main", true), context(linked, "task/x", false, active = true))
        assertThat(contextSelectorLabel(contexts)).isEqualTo("task/x")
        assertThat(contextSelectorLabel(contexts.map { it.copy(active = it.main) })).isEqualTo("main folder · main")
        assertThat(contextSelectorLabel(emptyList())).isEqualTo("main folder")
    }

    @Test
    fun `selector is hidden without a linked worktree`() {
        assertThat(contextSelectorVisible(listOf(context(main, "main", true)))).isFalse()
        assertThat(contextSelectorVisible(listOf(context(main, "main", true), context(linked, "task/x", false)))).isTrue()
    }

    @Test
    fun `picking the main folder switches to null and a worktree to its folder`() {
        assertThat(context(main, "main", true).switchTarget()).isNull()
        assertThat(context(linked, "task/x", false).switchTarget()).isEqualTo(linked)
    }

    @Test
    fun `banner of a task has branch, base and the first non-blank task line`() {
        val task = TaskWorktree(1, 1, linked, "task/x", "main", "\n  Fix the login redirect  \nmore detail")

        assertThat(workBanner("task/x", task, true)).isEqualTo(WorkBanner("task/x", "main", "Fix the login redirect", true))
    }

    @Test
    fun `banner of a worktree without a task row names only the branch`() {
        assertThat(workBanner("feature/y", null, true)).isEqualTo(WorkBanner("feature/y", null, null, false))
        assertThat(workBanner(null, null, true)?.branch).isEqualTo("detached")
    }

    @Test
    fun `no banner in the main folder without a task`() {
        assertThat(workBanner("main", null, false)).isNull()
    }

    @Test
    fun `a blank base branch is treated as unknown`() {
        assertThat(workBanner("task/x", TaskWorktree(1, 1, linked, "task/x", "", "x"), true)?.base).isNull()
    }
}
