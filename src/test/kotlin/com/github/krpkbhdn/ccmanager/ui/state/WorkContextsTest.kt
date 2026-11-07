package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.Worktree
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class WorkContextsTest {
    private val main = Path.of("repo")
    private val linked = Path.of("repo-task-x")
    private val worktrees = listOf(
        Worktree(linked, "task/x", "abc", false, false, false, false, true, false),
        Worktree(main, "main", "def", true, false, false, false, false, false),
    )
    private val task = TaskWorktree(1, 1, linked, "task/x", "main", "x")

    @Test
    fun `lists the main folder first with task, session and active flag`() {
        val contexts = workContexts(main, worktrees, listOf(task), mapOf("ccm-1" to linked), linked)

        assertThat(contexts.map { it.directory }).containsExactly(main, linked)
        assertThat(contexts.map { it.active }).containsExactly(false, true)
        assertThat(contexts[1].task).isEqualTo(task)
        assertThat(contexts[1].sessionId).isEqualTo("ccm-1")
        assertThat(contexts[1].dirty).isTrue()
        assertThat(contexts[0].task).isNull()
        assertThat(contexts[0].sessionId).isNull()
    }

    @Test
    fun `no active worktree means the main folder is active`() {
        assertThat(workContexts(main, worktrees, emptyList(), emptyMap(), null).map { it.active }).containsExactly(true, false)
    }

    @Test
    fun `a worktree list not loaded yet still yields the main folder`() {
        val contexts = workContexts(main, emptyList(), emptyList(), emptyMap(), null)

        assertThat(contexts).hasSize(1)
        assertThat(contexts[0].main).isTrue()
        assertThat(contexts[0].branch).isNull()
    }

    @Test
    fun `review sessions of a worktree are only the ones running there, never a fallback`() {
        val inMain = session("a")
        val inWorktree = session("b")
        val directories = mapOf("a" to main, "b" to linked)

        assertThat(sessionsWorkingIn(listOf(inMain, inWorktree), directories, linked, main)).containsExactly(inWorktree)
        assertThat(sessionsWorkingIn(listOf(inMain), directories, linked, main)).isEmpty()
    }

    @Test
    fun `review sessions of the main folder exclude worktree sessions and include ones with no recorded folder`() {
        val inMain = session("a")
        val inWorktree = session("b")
        val unrecorded = session("c")
        val directories = mapOf("a" to main, "b" to linked)

        assertThat(sessionsWorkingIn(listOf(inMain, inWorktree, unrecorded), directories, null, main)).containsExactly(inMain, unrecorded)
        assertThat(sessionsWorkingIn(listOf(inWorktree), directories, null, main)).isEmpty()
    }

    @Test
    fun `badge is the branch of a linked worktree and absent for the main folder or elsewhere`() {
        assertThat(worktreeBadge(worktrees, linked)).isEqualTo("task/x")
        assertThat(worktreeBadge(worktrees, main)).isNull()
        assertThat(worktreeBadge(worktrees, Path.of("elsewhere"))).isNull()
        assertThat(worktreeBadge(worktrees, null)).isNull()
        assertThat(worktreeBadge(emptyList(), linked)).isNull()
    }

    @Test
    fun `badge of a detached worktree is its folder name`() {
        assertThat(worktreeBadge(listOf(Worktree(linked, "", "abc", false, true, false, false, false, false)), linked)).isEqualTo("repo-task-x")
    }

    private fun session(id: String): SessionSnapshot = mock(SessionSnapshot::class.java).also { `when`(it.ccmSessionId).thenReturn(id) }
}
