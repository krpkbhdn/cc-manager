package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.github.CheckSummary
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestLookup
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitStalePullRequestTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `checking out another branch drops the pull request of the previous one and the cleanup offer`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(".git"), "gitdir: elsewhere")
        val data = SampleRepos.busy().also { it.topLevel = dir }
        val b = FakeGit(data).backend
        val head = data.status().head()
        val merged = PullRequest(7, "u", PullRequestState.MERGED, CheckSummary.NONE, "release", head)
        val github = object : GitHubPort {
            override val pollInterval: Duration = Duration.ofMillis(10)
            override fun availability(directory: Path) = GhAvailability.READY
            override fun lookup(directory: Path, branch: String) = PullRequestLookup.found(merged)
        }
        val backend = GitBackend(b.status, b.index, b.branches, b.sync, b.log, b.stashes, b.history, b.conflicts, b.worktrees, b.watcher, b.taskWorktrees, github)
        val git = GitState(1, dir, backend, scope, GitHost({ GitPrefs() }, { }, { _, _, _ -> }))
        data.branch = "feature/x"
        runBlocking { git.reloadNow(); git.refreshPullRequest() }
        assertThat(git.mergedCleanupAvailable()).isTrue()

        data.branch = "other"
        runBlocking { git.reloadNow() }

        assertThat(git.pullRequest).isNull()
        assertThat(git.mergedCleanupAvailable()).isFalse()
    }
}
