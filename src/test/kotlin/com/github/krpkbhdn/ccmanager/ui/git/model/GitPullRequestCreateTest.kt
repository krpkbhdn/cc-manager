package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.github.CheckSummary
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestDraft
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import java.io.IOException
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GitPullRequestCreateTest {
    private val form = PullRequestForm("main", "feature/x", "origin", "Add x", "Does x", "o/r")

    @Test
    fun `base with odd characters is rejected and falls back to main`() {
        assertThat(safeBase("develop")).isEqualTo("develop")
        assertThat(safeBase("release/1.2")).isEqualTo("release/1.2")
        assertThat(listOf("--exec", "a b", "a;rm", "x`y", "\$(id)", "", "..", "a..b")).allSatisfy { assertThat(safeBase(it)).isNull() }
        assertThat(defaultBase("bad name")).isEqualTo("main")
        assertThat(defaultBase(null)).isEqualTo("main")
        assertThat(defaultBase("develop")).isEqualTo("develop")
    }

    @Test
    fun `button shows only for a ready gh and no open pull request`() {
        val open = PullRequest(1, "u", PullRequestState.OPEN, CheckSummary.NONE, "main")
        val merged = PullRequest(1, "u", PullRequestState.MERGED, CheckSummary.NONE, "main")
        assertThat(canCreatePullRequest("f", GhAvailability.READY, null, true)).isTrue()
        assertThat(canCreatePullRequest("f", GhAvailability.READY, merged, true)).isTrue()
        assertThat(canCreatePullRequest("f", GhAvailability.READY, open, true)).isFalse()
        assertThat(canCreatePullRequest("f", GhAvailability.MISSING, null, true)).isFalse()
        assertThat(canCreatePullRequest("f", GhAvailability.NOT_AUTHENTICATED, null, true)).isFalse()
        assertThat(canCreatePullRequest("f", null, null, true)).isFalse()
        assertThat(canCreatePullRequest(null, GhAvailability.READY, null, true)).isFalse()
    }

    @Test
    fun `pull request and ci chip are hidden without a github remote`() {
        assertThat(canCreatePullRequest("f", GhAvailability.READY, null, false)).isFalse()
        assertThat(pullRequestChip(GhAvailability.MISSING, null, false)).isNull()
        assertThat(isGithubRemote("git@github.com:o/r.git")).isTrue()
        assertThat(isGithubRemote("https://gitlab.com/o/r.git")).isFalse()
        assertThat(isGithubRemote("/srv/origin.git")).isFalse()
        assertThat(isGithubRemote(null)).isFalse()
    }

    @Test
    fun `remote comes from the upstream, else the only remote, else origin`() {
        assertThat(pullRequestRemote("fork/feature", listOf("origin", "fork"))).isEqualTo("fork")
        assertThat(pullRequestRemote(null, listOf("up"))).isEqualTo("up")
        assertThat(pullRequestRemote(null, listOf("a", "b"))).isEqualTo("origin")
    }

    @Test
    fun `confirm text lists files, commits, repo, push and gh arguments`() {
        val text = confirmText(form, listOf("a.txt", "b/c.kt"), listOf("abc123 Add x"))
        assertThat(text).contains("Add x", "a.txt", "b/c.kt", "git push -u origin feature/x, uploading 1 commit(s)", "abc123 Add x",
            "gh pr create --repo o/r --base main --head feature/x", "Title: Add x", "Does x").doesNotContain("git add -A", "WARNING")
        assertThat(confirmText(form, emptyList(), emptyList())).doesNotContain("git add").contains("1. git push -u origin feature/x")
    }

    @Test
    fun `confirm warns about files that look like secrets`() {
        assertThat(secretLookingFiles(listOf(".env", "cfg/.env.local", "k/server.pem", "id_rsa", "a/credentials.json", "src/Main.kt", "environment.kt")))
            .containsExactly(".env", "cfg/.env.local", "k/server.pem", "id_rsa", "a/credentials.json")
        assertThat(confirmText(form, listOf(".env"), emptyList())).contains("WARNING", ".env")
    }

    @Test
    fun `repository comes from https, ssh and scp urls`() {
        assertThat(githubRepository("https://github.com/o/r.git")).isEqualTo("o/r")
        assertThat(githubRepository("git@github.com:o/r.git")).isEqualTo("o/r")
        assertThat(githubRepository("ssh://git@github.com/o/r")).isEqualTo("o/r")
        assertThat(githubRepository("https://github.com/owner")).isNull()
        assertThat(isGithubRemote("https://github.com/owner")).isFalse()
        assertThat(githubRepository("not a url")).isNull()
        assertThat(githubRepository(null)).isNull()
    }

    @Test
    fun `form needs a title, safe branches, a feature branch and a repository`() {
        assertThat(form.problem()).isNull()
        assertThat(form.copy(title = " ").problem()).isNotNull()
        assertThat(form.copy(title = "a\nb").problem()).isNotNull()
        assertThat(form.copy(base = "--x").problem()).isNotNull()
        assertThat(form.copy(head = "feature/x\" --base \"evil").problem()).isNotNull()
        assertThat(form.copy(head = "main").problem()).isNotNull()
        assertThat(form.copy(head = "main", base = "main").problem()).isNotNull()
        assertThat(form.copy(base = "feature/x").problem()).isEqualTo("Head and base are the same branch")
        assertThat(form.copy(repo = "").problem()).isNotNull()
        assertThat(form.copy(title = "Fix \"quoted\" bug").problem()).isNull()
    }

    private class RecordingGitHub(val calls: MutableList<String>, val failure: IOException? = null) : GitHubPort {
        override val pollInterval = java.time.Duration.ofMillis(10)
        override fun availability(directory: Path) = GhAvailability.READY
        override fun lookup(directory: Path, branch: String) = com.github.krpkbhdn.ccmanager.github.PullRequestLookup.none()
        override fun create(directory: Path, draft: PullRequestDraft): String {
            calls += "gh ${draft.repo()} ${draft.base()} ${draft.head()} ${draft.title()}"
            failure?.let { throw it }
            return "https://github.com/o/r/pull/9"
        }
    }

    private fun backend(data: com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData, github: GitHubPort): GitBackend {
        val b = FakeGit(data).backend
        return GitBackend(b.status, b.index, b.branches, b.sync, b.log, b.stashes, b.history, b.conflicts, b.worktrees, b.watcher, b.taskWorktrees, github)
    }

    private fun publish(data: com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData, gh: GitHubPort, files: List<String>, confirmedHead: String? = data.status().head()): String {
        data.branch = "feature/x"
        return backend(data, gh).publishPullRequest(Path.of("."), form, files, confirmedHead) {}
    }

    @Test
    fun `steps run commit then push then gh and the url is returned`() {
        val data = SampleRepos.busy()
        val files = data.changes.map { it.path() }
        val gh = RecordingGitHub(data.calls)
        val url = publish(data, gh, files)
        assertThat(url).isEqualTo("https://github.com/o/r/pull/9")
        assertThat(data.calls.map { it.substringBefore(' ') }).containsSubsequence("stage", "commit", "push", "gh")
        assertThat(data.calls.single { it.startsWith("commit") }).isEqualTo("commit Add x")
    }

    @Test
    fun `a clean tree skips the commit and a failed push never reaches gh`() {
        val data = SampleRepos.busy().also { it.changes.clear() }
        val gh = RecordingGitHub(data.calls)
        data.failure = GitException(GitException.Kind.NON_FAST_FORWARD, "rejected", "")
        assertThatThrownBy { publish(data, gh, emptyList()) }.isInstanceOf(GitException::class.java)
        assertThat(data.calls.map { it.substringBefore(' ') }).doesNotContain("commit", "stage", "gh")
    }

    @Test
    fun `publishing aborts when the dirty files differ from the confirmed ones`() {
        val data = SampleRepos.busy()
        val gh = RecordingGitHub(data.calls)
        val confirmed = data.changes.map { it.path() }.drop(1)
        assertThatThrownBy { publish(data, gh, confirmed) }
            .isInstanceOf(GitException::class.java).hasMessageContaining("changed after the confirmation")
        assertThat(data.calls.map { it.substringBefore(' ') }).doesNotContain("commit", "stage", "push", "gh")
    }

    @Test
    fun `a failing gh surfaces as a git exception without its output`() {
        val data = SampleRepos.busy().also { it.changes.clear() }
        val gh = RecordingGitHub(data.calls, IOException("gh pr create failed with exit code 1"))
        assertThatThrownBy { publish(data, gh, emptyList()) }
            .isInstanceOf(GitException::class.java).hasMessage("gh pr create failed with exit code 1")
    }

    @Test
    fun `a branch or head that moved after the confirmation is refused before anything is staged`() {
        val data = SampleRepos.busy()
        val gh = RecordingGitHub(data.calls)
        val files = data.changes.map { it.path() }
        data.branch = "feature/other"
        assertThatThrownBy { backend(data, gh).publishPullRequest(Path.of("."), form, files, data.status().head()) {} }.hasMessageContaining("changed after the confirmation")
        assertThatThrownBy { publish(data, gh, files, "otherhead") }.hasMessageContaining("changed after the confirmation")
        assertThat(data.calls.map { it.substringBefore(' ') }).doesNotContain("commit", "stage", "push", "gh")
    }

    @Test
    fun `an operation in progress or conflicted files are refused before anything is staged`() {
        val gh = RecordingGitHub(mutableListOf())
        val merging = SampleRepos.merging()
        assertThatThrownBy { publish(merging, gh, merging.changes.map { it.path() }) }.hasMessageContaining("in progress")
        val conflicted = SampleRepos.merging().apply { operation = com.github.krpkbhdn.ccmanager.git.GitStatus.Operation.NONE }
        assertThatThrownBy { publish(conflicted, gh, conflicted.changes.map { it.path() }) }.hasMessageContaining("unresolved conflicts")
        assertThat(merging.calls.map { it.substringBefore(' ') }).doesNotContain("commit", "stage", "push", "gh")
    }
}
