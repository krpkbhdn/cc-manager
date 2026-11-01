package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.TaskWorktreeService
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.ui.PreviewWorld
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.queue.QueueState
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsState
import com.pty4j.PtyProcess
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class TaskDialogStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val opened = CopyOnWriteArrayList<String>()
    private val errors = CopyOnWriteArrayList<String>()
    private val createdFor = CopyOnWriteArrayList<Long>()
    private val queue = mock(QueueState::class.java)
    private val terminal = object : TerminalPort {
        override fun open(session: LaunchedSession) = Unit
        override fun send(ccmSessionId: String, text: String) = Unit
        override fun close(ccmSessionId: String) = Unit

        @Composable
        override fun View(ccmSessionId: String, modifier: Modifier) = Unit
    }
    private val world = PreviewWorld()
    private val backend = world.backend(terminal)
    private val worktrees = mock(TaskWorktreeService::class.java)
    private val worktree = TaskWorktree(5, 1, Path.of("repo-task-x"), "task/x", "main", "x")
    private val sessions = SessionsState(
        backend, scope, { error("not used") }, { null }, { null },
        onOpened = { _, id -> opened += id }, onClosed = { _, _ -> }, showProject = {}, requestConfirm = {}, reportError = { errors += it },
    )

    @AfterEach
    fun stop() = scope.cancel()

    private fun tasks() = TaskDialogState(
        sessions, mock(TeamsState::class.java), queue, scope, worktrees, { errors += it }, { "main" },
        { _, sessionId -> "task/of-$sessionId" }, { createdFor += it },
    )

    private fun project() = Project(1, "repo", Path.of("repo"), Instant.EPOCH, ModelSelection.DEFAULT)

    @Test
    fun `an isolated task creates the worktree and starts the session in it under the parent project`() {
        `when`(worktrees.create(1, Path.of("repo"), "x")).thenReturn(worktree)
        doReturn(LaunchedSession("ccm-1", 1, "claude", mock(PtyProcess::class.java))).`when`(backend.launcher).launchTaskIn(1, worktree.path(), null, "x")
        val tasks = tasks()

        tasks.start(project(), null, "x", false, true)
        await { "ccm-1" in opened }

        verify(backend.launcher).launchTaskIn(1, worktree.path(), null, "x")
        assertThat(sessions.sessionDirectories["ccm-1"]).isEqualTo(worktree.path())
        assertThat(createdFor).containsExactly(1L)
        await { !tasks.starting }
    }

    @Test
    fun `a failed launch names the worktree that stays behind in a single message`() {
        `when`(worktrees.create(1, Path.of("repo"), "x")).thenReturn(worktree)
        doThrow(IllegalStateException("boom")).`when`(backend.launcher).launchTaskIn(1, worktree.path(), null, "x")
        val tasks = tasks()

        tasks.start(project(), null, "x", false, true)
        await { errors.any { "repo-task-x" in it } }

        await { !tasks.starting }
        assertThat(errors).hasSize(1)
        assertThat(errors.single()).startsWith("boom")
    }

    @Test
    fun `starting is reset when creating the worktree throws a runtime exception`() {
        `when`(worktrees.create(1, Path.of("repo"), "x")).thenThrow(IllegalStateException("no space"))
        val tasks = tasks()

        tasks.start(project(), null, "x", false, true)

        await { errors == listOf("no space") }
        await { !tasks.starting }
    }

    @Test
    fun `starting is reset when creating the worktree throws something that is not a runtime exception`() {
        `when`(worktrees.create(1, Path.of("repo"), "x")).thenThrow(AssertionError("broken"))
        val tasks = tasks()

        tasks.start(project(), null, "x", false, true)

        await { !tasks.starting && errors.isNotEmpty() }
        assertThat(errors).containsExactly("broken")
    }

    @Test
    fun `a second start while one is being set up is ignored`() {
        val release = CountDownLatch(1)
        `when`(worktrees.create(1, Path.of("repo"), "x")).thenAnswer { release.await(); worktree }
        doReturn(LaunchedSession("ccm-1", 1, "claude", mock(PtyProcess::class.java))).`when`(backend.launcher).launchTaskIn(1, worktree.path(), null, "x")
        val tasks = tasks()

        tasks.start(project(), null, "x", false, true)
        await { tasks.starting }
        tasks.start(project(), null, "x", false, true)
        release.countDown()

        await { "ccm-1" in opened }
        await { !tasks.starting }
        verify(worktrees, times(1)).create(1, Path.of("repo"), "x")
    }

    @Test
    fun `a template queued for a session takes the branch of the folder that session runs in`() {
        val template = PromptTemplate(1, "t", "", "review {branch}", null, null)

        tasks().queueFromTemplate(project(), "ccm-9", template)

        verify(queue).add("ccm-9", "review task/of-ccm-9")
    }
}
