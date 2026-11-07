package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitMisc
import com.github.krpkbhdn.ccmanager.github.CheckSummary
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestLookup
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
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

class GitPullRequestOpsTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toasts = mutableListOf<NotificationKind>()

    @AfterEach
    fun stop() = scope.cancel()

    private fun pr(state: PullRequestState = PullRequestState.OPEN, checks: CheckSummary = CheckSummary.PASSING) = PullRequest(7, "https://github.com/o/r/pull/7", state, checks, "main")

    private class FakeGitHub(var availability: GhAvailability, var result: PullRequestLookup) : PullRequestPort {
        var views = 0
        override val pollInterval: Duration = Duration.ofMillis(10)
        override fun availability(directory: Path, remote: ForgeRemote) = availability
        override fun lookup(directory: Path, remote: ForgeRemote, branch: String): PullRequestLookup { views++; return result }
    }

    private fun open(repo: Path, github: PullRequestPort, remoteUrl: String = "git@github.com:o/r.git"): GitState {
        val data = SampleRepos.busy().also { it.topLevel = repo; it.remotes = listOf(GitMisc.Remote("origin", remoteUrl, remoteUrl)) }
        val b = FakeGit(data).backend
        val backend = GitBackend(b.status, b.index, b.branches, b.sync, b.log, b.stashes, b.history, b.conflicts, b.worktrees, b.watcher, b.taskWorktrees, github)
        val git = GitState(1, repo, backend, scope, GitHost({ GitPrefs() }, { }, { kind, _, _ -> toasts += kind }))
        runBlocking { git.reloadNow() }
        return git
    }

    @Test
    fun `a linked worktree with a ready gh shows its pull request`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(".git"), "gitdir: elsewhere")
        val git = open(dir, FakeGitHub(GhAvailability.READY, PullRequestLookup.found(pr())))

        runBlocking { git.refreshPullRequest() }

        assertThat(git.ghStatus).isEqualTo(GhAvailability.READY)
        assertThat(git.pullRequest!!.number()).isEqualTo(7)
    }

    @Test
    fun `a failed lookup or an undecided gh keeps the last pull request and status`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(".git"), "gitdir: elsewhere")
        val gh = FakeGitHub(GhAvailability.READY, PullRequestLookup.found(pr()))
        val git = open(dir, gh)
        runBlocking { git.refreshPullRequest() }

        gh.result = PullRequestLookup.failure()
        runBlocking { git.refreshPullRequest() }
        assertThat(git.pullRequest!!.number()).isEqualTo(7)

        gh.availability = GhAvailability.UNKNOWN
        runBlocking { git.refreshPullRequest() }
        assertThat(git.ghStatus).isEqualTo(GhAvailability.READY)
        assertThat(git.pullRequest).isNotNull()

        gh.availability = GhAvailability.READY
        gh.result = PullRequestLookup.none()
        runBlocking { git.refreshPullRequest() }
        assertThat(git.pullRequest).isNull()
    }

    @Test
    fun `a missing gh is a state and gh is not asked for the pull request`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(".git"), "gitdir: elsewhere")
        val gh = FakeGitHub(GhAvailability.MISSING, PullRequestLookup.found(pr()))
        val git = open(dir, gh)

        runBlocking { git.refreshPullRequest() }

        assertThat(git.ghStatus).isEqualTo(GhAvailability.MISSING)
        assertThat(git.pullRequest).isNull()
        assertThat(gh.views).isZero()
        assertThat(git.error).isNull()
    }

    @Test
    fun `a gitlab remote is asked for its merge request`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(".git"), "gitdir: elsewhere")
        val gh = FakeGitHub(GhAvailability.READY, PullRequestLookup.found(pr()))
        val git = open(dir, gh, "git@gitlab.example.com:g/s/r.git")

        runBlocking { git.refreshPullRequest() }

        assertThat(git.forgeRemote!!.forge).isEqualTo(Forge.GITLAB)
        assertThat(git.pullRequest!!.number()).isEqualTo(7)
    }

    @Test
    fun `an unknown remote is never polled`(@TempDir dir: Path) {
        Files.writeString(dir.resolve(".git"), "gitdir: elsewhere")
        val gh = FakeGitHub(GhAvailability.READY, PullRequestLookup.found(pr()))
        val git = open(dir, gh, "git@example.com:acme/app.git")

        runBlocking { git.refreshPullRequest() }

        assertThat(git.pullRequest).isNull()
        assertThat(gh.views).isZero()
    }

    @Test
    fun `the main worktree is never polled`(@TempDir dir: Path) {
        Files.createDirectory(dir.resolve(".git"))
        val gh = FakeGitHub(GhAvailability.READY, PullRequestLookup.found(pr()))
        val git = open(dir, gh)

        runBlocking { git.refreshPullRequest() }

        assertThat(git.pullRequest).isNull()
        assertThat(gh.views).isZero()
    }

    @Test
    fun `chip texts and tones follow availability and checks`() {
        val github = ForgeRemote(Forge.GITHUB, "github.com", "o/r")
        assertThat(pullRequestChip(null, null, github)).isNull()
        assertThat(pullRequestChip(GhAvailability.READY, null, github)).isNull()
        assertThat(pullRequestChip(GhAvailability.READY, pr(), null)).isNull()
        assertThat(pullRequestChip(GhAvailability.MISSING, null, github)!!.hint).isEqualTo("Install GitHub CLI: https://cli.github.com")
        assertThat(pullRequestChip(GhAvailability.NOT_AUTHENTICATED, null, github)!!.text).isEqualTo("GitHub CLI not signed in")
        assertThat(pullRequestChip(GhAvailability.READY, pr(checks = CheckSummary.FAILING), github)!!.tone).isEqualTo(ChipTone.DANGER)
        assertThat(pullRequestChip(GhAvailability.READY, pr(checks = CheckSummary.PENDING), github)!!.tone).isEqualTo(ChipTone.WARNING)
        val merged = pullRequestChip(GhAvailability.READY, pr(PullRequestState.MERGED), github)!!
        assertThat(merged.text).isEqualTo("PR #7 merged")
        assertThat(merged.tone).isEqualTo(ChipTone.NEUTRAL)
        assertThat(merged.url).isEqualTo("https://github.com/o/r/pull/7")
    }

    @Test
    fun `a gitlab chip speaks of merge requests and glab`() {
        val gitlab = ForgeRemote(Forge.GITLAB, "gitlab.example.com", "https://gitlab.example.com/g/r")
        assertThat(pullRequestChip(GhAvailability.MISSING, null, gitlab)!!.hint).isEqualTo("Install GitLab CLI: https://gitlab.com/gitlab-org/cli")
        assertThat(pullRequestChip(GhAvailability.NOT_AUTHENTICATED, null, gitlab)!!.hint).isEqualTo("Run `glab auth login --hostname gitlab.example.com` in a terminal")
        val chip = pullRequestChip(GhAvailability.READY, pr(checks = CheckSummary.PASSING), gitlab)!!
        assertThat(chip.text).isEqualTo("MR !7 checks passing")
        assertThat(chip.hint).isEqualTo("Open merge request !7 on gitlab.example.com")
    }
}
