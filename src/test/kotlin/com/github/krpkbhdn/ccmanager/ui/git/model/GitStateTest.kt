package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.GraphState
import com.github.krpkbhdn.ccmanager.ui.git.log.LOG_PAGE_SIZE
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData
import com.github.krpkbhdn.ccmanager.ui.git.SampleRepos
import java.nio.file.Path
import java.util.Collections
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class GitStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val toasts: MutableList<Pair<NotificationKind, String>> = Collections.synchronizedList(mutableListOf())
    @Volatile private var prefs = GitPrefs()

    @AfterEach
    fun stop() = scope.cancel()

    private fun open(data: FakeRepoData = SampleRepos.busy()): Pair<GitState, FakeRepoData> {
        val host = GitHost({ prefs }, { change -> prefs = change(prefs) }, { kind, title, _ -> toasts += kind to title })
        val git = GitState(1, Path.of("repo"), FakeGit(data).backend, scope, host)
        git.attach()
        await { git.status != null && (!git.status!!.repository() || git.branches.isNotEmpty()) }
        return git to data
    }

    private fun await(timeoutMillis: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition()) {
            check(System.currentTimeMillis() < deadline) { "condition not met in time" }
            Thread.sleep(10)
        }
    }

    private fun paths(git: GitState, staged: Boolean) = changeSections(git.status!!.changes()).let { if (staged) it.staged else it.unstaged }.map { it.path() }

    @Test
    fun `opening loads status branches and the auxiliary lists`() {
        val (git, _) = open()

        await { git.stashes.size == 2 && git.tags.size == 3 && git.identity != null }
        assertThat(git.status!!.branch()).isEqualTo("feature/git-module")
        assertThat(git.summary!!.dirty).isEqualTo(7)
        assertThat(git.remotes.map { it.name() }).containsExactly("origin")
    }

    @Test
    fun `a project in a repository subfolder works on the repository top level`() {
        val data = SampleRepos.busy().also { it.topLevel = Path.of("top") }
        val (git, _) = open(data)

        assertThat(git.repo).isEqualTo(Path.of("top"))
        assertThat(data.statusRepos).containsOnly(Path.of("top"))
    }

    @Test
    fun `a top level that changes after opening is used by the next refresh`() {
        val (git, data) = open()
        data.topLevel = Path.of("moved")

        git.refresh()

        await { data.statusRepos.lastOrNull() == Path.of("moved") }
        assertThat(git.repo).isEqualTo(Path.of("moved"))
    }

    @Test
    fun `a failing top level shows a load error and the next reload recovers`() {
        val data = SampleRepos.busy().also { it.topLevelFailure = GitException(GitException.Kind.OTHER, "dubious ownership", "") }
        val host = GitHost({ prefs }, { change -> prefs = change(prefs) }, { kind, title, _ -> toasts += kind to title })
        val git = GitState(1, Path.of("repo"), FakeGit(data).backend, scope, host)
        git.attach()
        await { git.loadError != null }

        git.refresh()

        await { git.loadError == null && git.status != null }
    }

    @Test
    fun `the watcher subscribes to the project folder`() {
        val (_, data) = open(SampleRepos.busy().also { it.topLevel = Path.of("top") })

        assertThat(data.watchedPaths).containsExactly(Path.of("repo"))
    }

    @Test
    fun `staging a file moves it between the lists after the refresh`() {
        val (git, data) = open()

        git.stage(listOf("build.gradle.kts"))

        await { "build.gradle.kts" in paths(git, staged = true) }
        assertThat(paths(git, staged = false)).doesNotContain("build.gradle.kts")
        assertThat(data.calls).contains("stage build.gradle.kts")
    }

    @Test
    fun `a commit clears the draft and reports the short hash`() {
        val (git, _) = open()
        git.draft.message.edit { replace(0, length, "Add the status service") }

        git.commit(git.draft.request()) { git.draft.clear() }

        await { toasts.any { it.second.startsWith("Committed c0ffee1") } }
        assertThat(git.draft.message.text.toString()).isEmpty()
        assertThat(paths(git, staged = true)).isEmpty()
    }

    @Test
    fun `a failing hook opens its output instead of just a toast`() {
        val (git, _) = open()

        git.commit(com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest("hook-fail", false, false, false))

        await { git.dialog is GitDialog.Output }
        assertThat((git.dialog as GitDialog.Output).text).contains("lint failed")
        assertThat(toasts.map { it.first }).contains(NotificationKind.ERROR)
    }

    @Test
    fun `nothing to commit becomes an error strip`() {
        val (git, _) = open(SampleRepos.clean())

        git.commit(com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest("msg", false, false, false))

        await { git.error != null }
        assertThat(git.error!!.kind).isEqualTo(GitException.Kind.NOTHING_TO_COMMIT)
    }

    @Test
    fun `a dirty checkout offers to stash and switch`() {
        val (git, data) = open()

        git.checkout("dirty-branch")
        await { git.dialog is GitDialog.DirtyCheckout }
        git.dialog = null
        git.checkout("dirty-branch", autoStash = true)

        await { data.calls.any { it == "checkout dirty-branch autoStash=true" } }
    }

    @Test
    fun `a rejected push offers pull and force push`() {
        val (git, data) = open()
        data.failure = GitException(GitException.Kind.NON_FAST_FORWARD, "Push rejected", "! [rejected] main -> main (non-fast-forward)")

        git.push()

        await { git.error != null }
        assertThat(git.error!!.actions).contains(ErrorAction.PULL, ErrorAction.FORCE_PUSH)
        git.perform(ErrorAction.FORCE_PUSH)
        assertThat(git.dialog).isInstanceOf(GitDialog.Confirm::class.java)
        assertThat(git.error).isNull()
    }

    @Test
    fun `retry runs the failed action again`() {
        val (git, data) = open()
        data.failure = GitException(GitException.Kind.LOCKED, "index.lock exists", "index.lock")

        git.stage(listOf("build.gradle.kts"))
        await { git.error != null }
        git.perform(ErrorAction.RETRY)

        await { "build.gradle.kts" in paths(git, staged = true) }
        assertThat(git.error).isNull()
    }

    @Test
    fun `only one mutation runs at a time and progress lines show`() {
        val (git, data) = open()
        data.progressDelayMillis = 150

        git.fetch()
        await { git.job != null }
        git.pull()
        await { git.job == null && data.calls.any { it == "fetch" } }

        assertThat(data.calls.filter { it.startsWith("pull") }).isEmpty()
        assertThat(toasts).contains(NotificationKind.INFO to "Git is busy")
    }

    @Test
    fun `publishing a branch without upstream sets the upstream`() {
        val (git, data) = open(SampleRepos.noUpstream())

        git.push()

        await { data.upstream != null }
        assertThat(data.upstream).isEqualTo("origin/feature/new-idea")
        assertThat(data.calls.last()).contains("setUpstream=true")
    }

    @Test
    fun `a watcher event refreshes the status`() {
        val (git, data) = open()
        await { data.watchers.isNotEmpty() }
        assertThat(git.status!!.behind()).isEqualTo(1)

        data.behind = 5
        data.fire()

        await { git.status!!.behind() == 5 }
    }

    @Test
    fun `a selection of a vanished file is dropped`() {
        val (git, data) = open()
        git.selected = ChangeRef("website/logo.png", staged = false)

        data.changes.removeAll { it.path() == "website/logo.png" }
        git.refresh()

        await { git.selected == null }
    }

    @Test
    fun `a repository that is not one shows no summary`() {
        val (git, _) = open(SampleRepos.notRepository())

        assertThat(git.summary).isNull()
        assertThat(git.status!!.repository()).isFalse()
    }

    @Test
    fun `stashing updates the stash list`() {
        val (git, _) = open()

        git.stashPush("parked", includeUntracked = false)

        await { git.stashes.first().message() == "parked" }
        assertThat(git.status!!.changes()).isEmpty()
    }

    @Test
    fun `the log pages and filters by author`() {
        val (git, _) = open()

        git.log.reload()
        await { git.log.rows.isNotEmpty() && !git.log.loading }
        val all = git.log.rows.size
        git.log.update { it.copy(author = "Priya") }
        await { !git.log.loading && git.log.rows.size < all }

        assertThat(git.log.rows).allMatch { it.commit().authorName() == "Priya N" }
        assertThat(git.log.hasMore).isFalse()
    }

    @Test
    fun `the next log page continues from the graph state of the previous page`() {
        val data = SampleRepos.busy()
        data.rows = List(LOG_PAGE_SIZE + 50) { withHash(data.rows[it % data.rows.size], "h$it") }
        val (git, _) = open(data)

        git.log.reload()
        await { git.log.rows.size == LOG_PAGE_SIZE && !git.log.loading }
        git.log.loadMore()
        await { git.log.rows.size == LOG_PAGE_SIZE + 50 && !git.log.loading }

        assertThat(data.logContinuations).containsExactly(null, GraphState(emptyList(), LOG_PAGE_SIZE))
    }

    @Test
    fun `a log page repeating an already shown commit does not duplicate its row`() {
        val data = SampleRepos.busy()
        val first = data.rows.first()
        data.rows = List(LOG_PAGE_SIZE) { withHash(first, "h$it") } + withHash(first, "h0") + withHash(first, "tail")
        val (git, _) = open(data)

        git.log.reload()
        await { git.log.rows.size == LOG_PAGE_SIZE && !git.log.loading }
        git.log.loadMore()
        await { git.log.rows.size == LOG_PAGE_SIZE + 1 && !git.log.loading }

        assertThat(git.log.rows.map { it.commit().hash() }).doesNotHaveDuplicates()
    }

    private fun withHash(row: GitLog.Row, hash: String): GitLog.Row {
        val commit = row.commit()
        return GitLog.Row(
            GitLog.Commit(hash, hash, commit.parents(), commit.authorName(), commit.authorEmail(), commit.authorDate(), commit.subject(), commit.body(), commit.refs()),
            row.lane(), row.color(), row.laneCount(), row.links(),
        )
    }

    @Test
    fun `selecting a commit loads its details and first file`() {
        val (git, data) = open()
        git.log.reload()
        await { git.log.rows.isNotEmpty() }

        git.log.select(git.log.rows.first().commit().hash())

        await { git.log.details != null }
        assertThat(git.log.selectedFile).isEqualTo(data.detailFiles.first().path)
    }

    @Test
    fun `status operation is exposed for the banner`() {
        val (git, _) = open(SampleRepos.merging())

        assertThat(git.status!!.operation()).isEqualTo(GitStatus.Operation.MERGE)
        assertThat(changeSections(git.status!!.changes()).conflicts).hasSize(2)
    }

    @Test
    fun `a commit focus request is consumed once`() {
        val (git, _) = open()

        git.requestCommitFocus()

        assertThat(git.view).isEqualTo(GitSubView.CHANGES)
        assertThat(git.takeCommitFocusRequest()).isTrue()
        assertThat(git.takeCommitFocusRequest()).isFalse()
    }

    @Test
    fun `section choice is remembered per project`() {
        val (git, _) = open()

        git.show(GitSubView.LOG)

        assertThat(git.view).isEqualTo(GitSubView.LOG)
        assertThat(prefs.viewOf(2)).isEqualTo(GitSubView.CHANGES)
    }
}
