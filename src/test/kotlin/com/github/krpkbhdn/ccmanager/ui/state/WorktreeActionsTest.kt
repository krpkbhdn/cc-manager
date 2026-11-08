package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.git.TaskWorktreeService
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.run.RunEntry
import com.github.krpkbhdn.ccmanager.ui.run.RunState
import java.nio.file.Path
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.verifyNoInteractions
import org.mockito.Mockito.verifyNoMoreInteractions
import org.mockito.Mockito.`when`

class WorktreeActionsTest {
    private val directory = Path.of("repo-task-x")
    private val projects = mock(ProjectsState::class.java)
    private val sessions = mock(SessionsState::class.java)
    private val shells = mock(ShellsState::class.java)
    private val runs = mock(RunState::class.java)
    private val taskWorktrees = mock(TaskWorktreeService::class.java)
    private val terminal = mock(TerminalPort::class.java)
    private val gits = mock(ProjectGits::class.java)
    private val actions = WorktreeActions(projects, sessions, shells, runs, taskWorktrees, terminal) { gits }

    @Test
    fun `stopWork closes the sessions, shells and runs of the folder`() {
        actions.stopWork(directory)

        verify(sessions).closeIn(directory)
        verify(shells).closeIn(directory)
        verify(runs).stopRunningIn(directory)
    }

    @Test
    fun `nothing runs in a folder without sessions, shells or runs`() {
        assertThat(actions.hasRunningWork(directory)).isFalse()
    }

    @Test
    fun `a live session, a shell or a running run each count as running work`() {
        `when`(sessions.hasLiveIn(directory)).thenReturn(true)
        assertThat(actions.hasRunningWork(directory)).isTrue()
        `when`(sessions.hasLiveIn(directory)).thenReturn(false)

        `when`(shells.hasShellIn(directory)).thenReturn(true)
        assertThat(actions.hasRunningWork(directory)).isTrue()
        `when`(shells.hasShellIn(directory)).thenReturn(false)

        doReturn(listOf(mock(RunEntry::class.java))).`when`(runs).runningIn(directory)
        assertThat(actions.hasRunningWork(directory)).isTrue()
    }

    @Test
    fun `work is stopping while the terminal still ends processes`() {
        assertThat(actions.workStopping()).isFalse()
        `when`(terminal.closing()).thenReturn(true)
        assertThat(actions.workStopping()).isTrue()
    }

    @Test
    fun `forgetStored deletes the task row and the project registered at the folder`() {
        val registered = Project(4, "old", directory, Instant.EPOCH, ModelSelection.DEFAULT)
        `when`(projects.projectAt(directory)).thenReturn(registered)

        actions.forgetStored(directory)

        verify(taskWorktrees).forget(directory)
        verify(projects).remove(registered)
        verifyNoInteractions(gits)
    }

    @Test
    fun `forgetStored without a registered project only deletes the task row`() {
        actions.forgetStored(directory)

        verify(taskWorktrees).forget(directory)
        verify(projects).projectAt(directory)
        verifyNoMoreInteractions(projects)
    }

    @Test
    fun `forget only changes the in-memory state and touches no database`() {
        actions.forget(directory)

        verify(gits).forgetWorktree(directory)
        verifyNoInteractions(taskWorktrees, projects)
    }

    @Test
    fun `showWorktree switches the git view and starts a session only when asked`() {
        actions.showWorktree(1, directory, false)
        verify(gits).switchTo(1, directory)
        verifyNoInteractions(sessions)

        actions.showWorktree(1, directory, true)
        verify(sessions).launchIn(1, directory, LaunchMode.NEW)
    }
}
