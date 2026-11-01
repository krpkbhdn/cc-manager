package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitPrefs
import java.nio.file.Path
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class ProjectGitsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val project = Project(1, "demo", Path.of("demo"), Instant.EPOCH, ModelSelection.DEFAULT)
    private val worktree = Path.of("demo-task-x")
    private val task = TaskWorktree(7, 1, worktree, "task/x", "main", "x")
    private val fake = FakeGit(SampleRepos.busy())
    private val taskReads = AtomicInteger()
    private var taskReadFailures = 0
    private val gits = ProjectGits(
        fake.backend, scope, GitHost({ GitPrefs() }, { }, { _, _, _ -> }),
        { id -> project.takeIf { it.id == id } },
        { taskReads.incrementAndGet(); if (taskReadFailures-- > 0) throw IllegalStateException("database is locked"); listOf(task) },
    )

    @AfterEach
    fun stop() = scope.cancel()

    private fun listWorktrees(vararg linked: Path) {
        fake.data.worktrees.clear()
        fake.data.worktrees += Worktree(project.path, "main", "b".repeat(40), true, false, false, false, false, false)
        linked.forEach { fake.data.worktrees += Worktree(it, "task/x", "a".repeat(40), false, false, false, false, false, false) }
    }

    private fun loadWorktreeList(): Unit = runBlocking {
        gits.mainOf(1)!!.reloadNow()
        gits.mainOf(1)!!.loadWorktrees()
    }

    @Test
    fun `of is the main state until a worktree is switched to`() {
        val main = gits.mainOf(1)

        assertThat(gits.of(1)).isSameAs(main)
        gits.switchTo(1, worktree)
        assertThat(gits.of(1)).isNotSameAs(main)
        assertThat(gits.mainOf(1)).isSameAs(main)
    }

    @Test
    fun `a worktree state is created once per path and carries its task row`() {
        gits.switchTo(1, worktree)
        val first = gits.of(1)
        gits.switchTo(1, null)
        gits.switchTo(1, worktree.toAbsolutePath().normalize())

        assertThat(gits.of(1)).isSameAs(first)
        await { first!!.task != null }
        assertThat(first!!.task).isEqualTo(task)
        assertThat(gits.mainOf(1)!!.task).isNull()
    }

    @Test
    fun `a task row that could not be read is read again on the next switch`() {
        taskReadFailures = 1
        gits.switchTo(1, worktree)
        val state = gits.of(1)!!
        await { taskReads.get() == 1 }
        assertThat(state.task).isNull()

        gits.switchTo(1, worktree)

        await { state.task != null }
        assertThat(state.task).isEqualTo(task)
    }

    @Test
    fun `switching to null or to the project folder shows the main state again`() {
        gits.switchTo(1, worktree)
        gits.switchTo(1, null)
        assertThat(gits.of(1)).isSameAs(gits.mainOf(1))

        gits.switchTo(1, worktree)
        gits.switchTo(1, project.path)
        assertThat(gits.activeDirectoryOf(1)).isNull()
    }

    @Test
    fun `a folder that is not a worktree of the project is dropped after the check`() {
        listWorktrees(worktree)
        loadWorktreeList()

        gits.switchTo(1, Path.of("somewhere-else"))
        await { gits.activeDirectoryOf(1) == null }

        assertThat(gits.of(1)).isSameAs(gits.mainOf(1))
        assertThat(gits.loaded()).hasSize(1)
    }

    @Test
    fun `a listed worktree stays active through the check`() {
        listWorktrees(worktree)
        loadWorktreeList()

        gits.switchTo(1, worktree)
        runBlocking { gits.syncWorktrees(1) }

        assertThat(gits.activeDirectoryOf(1)).isEqualTo(worktree.toAbsolutePath().normalize())
        assertThat(gits.loaded()).hasSize(2)
    }

    @Test
    fun `an active worktree that vanished falls back to main and its state is disposed`() {
        listWorktrees(worktree)
        loadWorktreeList()
        gits.switchTo(1, worktree)
        val vanished = gits.of(1)

        listWorktrees()
        runBlocking { gits.mainOf(1)!!.loadWorktrees(); gits.syncWorktrees(1) }

        assertThat(gits.activeDirectoryOf(1)).isNull()
        assertThat(gits.of(1)).isSameAs(gits.mainOf(1))
        assertThat(gits.loaded()).doesNotContain(vanished)
    }

    @Test
    fun `an empty worktree list never disposes anything`() {
        gits.switchTo(1, worktree)

        runBlocking { gits.syncWorktrees(1) }

        assertThat(gits.activeDirectoryOf(1)).isNotNull()
    }

    @Test
    fun `retain drops the states and the active worktree of projects that are gone`() {
        gits.mainOf(1)
        gits.switchTo(1, worktree)

        gits.retain(emptySet())

        assertThat(gits.loaded()).isEmpty()
        assertThat(gits.activeDirectoryOf(1)).isNull()
    }

    @Test
    fun `retain keeps the states of projects that stay`() {
        gits.mainOf(1)
        gits.switchTo(1, worktree)

        gits.retain(setOf(1))

        assertThat(gits.loaded()).hasSize(2)
        assertThat(gits.activeDirectoryOf(1)).isNotNull()
    }

    @Test
    fun `forgetting the active worktree falls back to main, drops its state and raises the task version`() {
        gits.mainOf(1)
        gits.switchTo(1, worktree)
        val removed = gits.of(1)
        val before = gits.taskVersion

        gits.forgetWorktree(worktree)

        assertThat(gits.of(1)).isSameAs(gits.mainOf(1))
        assertThat(gits.taskVersion).isGreaterThan(before)
        gits.switchTo(1, worktree)
        assertThat(gits.of(1)).isNotSameAs(removed)
    }

    @Test
    fun `a created task row raises the task version`() {
        val before = gits.taskVersion

        gits.taskRowsChanged(1)

        assertThat(gits.taskVersion).isEqualTo(before + 1)
    }

    @Test
    fun `branchOf and summaryOf follow the main folder`() {
        gits.mainOf(1)
        gits.switchTo(1, worktree)

        assertThat(gits.summaryOf(1)).isSameAs(gits.mainOf(1)!!.summary)
        assertThat(gits.loaded()).hasSize(2)
    }

    @Test
    fun `an unknown project has no state and switching is ignored`() {
        gits.switchTo(9, worktree)

        assertThat(gits.of(9)).isNull()
        assertThat(gits.activeDirectoryOf(9)).isNull()
    }
}
