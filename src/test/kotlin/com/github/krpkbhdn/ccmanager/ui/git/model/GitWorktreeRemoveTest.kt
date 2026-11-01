package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitRunner
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class GitWorktreeRemoveTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val data = FakeRepoData()
    private val main = Path.of("main")
    private val linked = Path.of("wt")
    private var removeFailures = 0
    private var dirty = false
    private var stopping = false
    private val worktree = Worktree(linked, "feature/x", "a".repeat(40), false, false, false, false, false, false)

    @AfterEach
    fun stop() = scope.cancel()

    private fun backend(): GitBackend {
        val fake = FakeGit(data).backend
        val worktrees = object : GitWorktreeService(GitRunner()) {
            override fun list(repo: Path): List<Worktree> = listOf(
                Worktree(main, "main", "b".repeat(40), true, false, false, false, false, false),
                Worktree(linked, "feature/x", "a".repeat(40), false, false, false, false, dirty, false),
            )

            override fun remove(repo: Path, worktree: Path, force: Boolean) {
                data.record("remove from=$repo force=$force")
                if (removeFailures-- > 0) throw GitException(GitException.Kind.OTHER, "file is in use", "")
            }
        }
        return GitBackend(fake.status, fake.index, fake.branches, fake.sync, fake.log, fake.stashes, fake.history, fake.conflicts, worktrees, fake.watcher, fake.taskWorktrees)
    }

    private fun host() = GitHost(
        { GitPrefs() }, { }, { _, title, _ -> data.record("toast $title") },
        showWorktree = { _, directory, _ -> data.record("show $directory") },
        forgetWorktree = { data.record("forget $it") },
        stopWorktreeWork = { data.record("stop") },
        workStopping = { stopping.also { stopping = false } },
        deleteRunConfigurations = { data.record("deleteConfigs") },
        forgetStoredWorktree = { data.record("forgetStored") },
    )

    private fun state(folder: Path) = GitState(1, folder, backend(), scope, host())

    @Test
    fun `a state showing the doomed folder switches to main first, removes from main and never reloads the vanished folder`() {
        val doomed = state(linked)

        doomed.removeWorktree(worktree, force = true)
        await { "forget $linked" in data.calls }

        assertThat(data.calls).containsExactly("stop", "show $main", "remove from=$main force=true", "forgetStored", "deleteConfigs", "forget $linked")
        assertThat(data.statusRepos).isEmpty()
    }

    @Test
    fun `another state removes the worktree from main and reloads itself`() {
        val other = state(main)

        other.removeWorktree(worktree, force = true)
        await { "forget $linked" in data.calls }
        await { data.statusRepos.isNotEmpty() }

        assertThat(data.calls).doesNotContain("show $main")
        assertThat(data.calls).contains("remove from=$main force=true")
    }

    @Test
    fun `a folder git still holds is removed again once`() {
        removeFailures = 1
        val other = state(main)

        other.removeWorktree(worktree, force = true)
        await { "forget $linked" in data.calls }

        assertThat(data.calls.count { it.startsWith("remove") }).isEqualTo(2)
    }

    @Test
    fun `a dirty worktree is refused before anything is stopped or removed`() {
        dirty = true
        val other = state(main)

        other.removeWorktree(worktree, force = false)
        await { data.calls.any { it.startsWith("toast") } }

        assertThat(data.calls).containsExactly("toast Could not remove the worktree")
    }

    @Test
    fun `the removal waits for processes that are still ending`() {
        stopping = true
        val other = state(main)

        other.removeWorktree(worktree, force = true)
        await { "forget $linked" in data.calls }

        assertThat(stopping).isFalse()
        assertThat(data.calls.indexOf("stop")).isLessThan(data.calls.indexOf("remove from=$main force=true"))
    }
}
