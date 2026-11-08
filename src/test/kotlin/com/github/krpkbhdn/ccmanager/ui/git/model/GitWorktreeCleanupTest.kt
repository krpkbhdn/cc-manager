package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitRunner
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.github.CheckSummary
import com.github.krpkbhdn.ccmanager.github.GhAvailability
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestLookup
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.util.Optional
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class GitWorktreeCleanupTest {
    private val head = "a".repeat(40)
    private val github = ForgeRemote(Forge.GITHUB, "github.com", "o/r")
    private val gitlab = ForgeRemote(Forge.GITLAB, "gitlab.com", "https://gitlab.com/g/r")
    private val plan = CleanupPlan(Path.of("wt"), "feature/x", head, Path.of("main"), listOf("web (x)", "api (x)"), remote = github)

    private fun pr(state: PullRequestState = PullRequestState.MERGED, oid: String = head, base: String = "main") = PullRequest(7, "u", state, CheckSummary.NONE, base, oid)

    private class Scene(
        val data: FakeRepoData = FakeRepoData(),
        var removeFailures: Int = 0,
        var worktreeHead: String,
        var branch: String = "feature/x",
        var dirty: Boolean = false,
        var present: Boolean = true,
        var pullRequest: PullRequest? = null,
        var branchCommit: String = "aaaaaaa",
        var deleteFails: Boolean = false,
        var lookupFails: Boolean = false,
        var ancestor: Boolean = false,
    )

    private fun scene(block: Scene.() -> Unit = {}) = Scene(worktreeHead = head, pullRequest = pr()).apply(block)

    private fun backend(s: Scene): GitBackend {
        val b = FakeGit(s.data).backend
        s.data.branches += GitBranch("feature/x", false, false, null, 0, 0, s.branchCommit, "s", Instant.now())
        val worktrees = object : GitWorktreeService(GitRunner()) {
            override fun list(repo: Path): List<Worktree> = buildList {
                add(Worktree(Path.of("main"), "main", "b".repeat(40), true, false, false, false, false, false))
                if (s.present) add(Worktree(Path.of("wt"), s.branch, s.worktreeHead, false, false, false, false, s.dirty, false))
            }
            override fun ignoredEntries(worktree: Path) = listOf(".env")
            override fun remove(repo: Path, worktree: Path, force: Boolean) {
                s.data.record("remove from=$repo force=$force")
                if (s.removeFailures-- > 0) throw GitException(GitException.Kind.OTHER, "file is in use", "")
                s.present = false
            }
        }
        val branches = object : com.github.krpkbhdn.ccmanager.git.GitBranchService(GitRunner()) {
            override fun branches(repo: Path) = s.data.branches.toList()
            override fun isAncestor(repo: Path, commit: String, branch: String) = s.ancestor
            override fun delete(repo: Path, name: String, force: Boolean) {
                s.data.record("delete $name force=$force")
                if (s.deleteFails) throw GitException(GitException.Kind.OTHER, "no such branch", "")
            }
        }
        val github = object : PullRequestPort {
            override val pollInterval: Duration = Duration.ofSeconds(1)
            override fun availability(directory: Path, remote: ForgeRemote) = GhAvailability.READY
            override fun lookup(directory: Path, remote: ForgeRemote, branch: String) =
                if (s.lookupFails) PullRequestLookup.failure() else PullRequestLookup(Optional.ofNullable(s.pullRequest), false)
        }
        return GitBackend(b.status, b.index, branches, b.sync, b.log, b.stashes, b.history, b.conflicts, worktrees, b.watcher, b.taskWorktrees, github)
    }

    private fun host(s: Scene) = GitHost({ GitPrefs() }, { }, { _, _, _ -> }, deleteRunConfigurations = { s.data.record("deleteConfigs") })

    private fun cleanup(s: Scene, p: CleanupPlan = plan) = backend(s).cleanupMergedWorktree(host(s), p, {}) {}

    private fun deleteCalls(s: Scene) = s.data.calls.filter { it.startsWith("delete ") }

    @Test
    fun `steps run remove from main, delete branch forced, delete configurations`() {
        val s = scene()
        assertThat(cleanup(s)).isNull()
        assertThat(s.data.calls).containsExactly("remove from=main force=false", "delete feature/x force=true", "deleteConfigs")
    }

    @Test
    fun `a failed gh lookup aborts the cleanup before anything is removed`() {
        val s = scene { lookupFails = true }
        assertThatThrownBy { cleanup(s) }.isInstanceOf(GitException::class.java).hasMessageContaining("Could not check the pull request")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `a locked folder is retried once`() {
        val s = scene { removeFailures = 1 }
        cleanup(s)
        assertThat(s.data.calls.count { it.startsWith("remove") }).isEqualTo(2)
    }

    @Test
    fun `a removal failing twice stops before the branch and configurations`() {
        val s = scene { removeFailures = 2 }
        assertThatThrownBy { cleanup(s) }.isInstanceOf(GitException::class.java).hasMessageContaining("Removing the worktree failed")
        assertThat(s.data.calls).noneMatch { it.startsWith("delete") }
    }

    @Test
    fun `a failing branch deletion still deletes the configurations and is reported`() {
        val s = scene { deleteFails = true }
        assertThat(cleanup(s)).contains("Deleting the branch feature/x failed", "removed the worktree")
        assertThat(s.data.calls).contains("deleteConfigs")
    }

    @Test
    fun `an already removed worktree is skipped and the rest continues`() {
        val s = scene { present = false }
        assertThat(cleanup(s)).isNull()
        assertThat(s.data.calls).containsExactly("delete feature/x force=true", "deleteConfigs")
    }

    @Test
    fun `a branch that moved since the plan is kept and reported`() {
        val s = scene { present = false; branchCommit = "ccccccc" }
        assertThat(cleanup(s)).contains("moved after")
        assertThat(deleteCalls(s)).isEmpty()
        assertThat(s.data.calls).contains("deleteConfigs")
    }

    @Test
    fun `nothing is deleted when the worktree head is not the merged pull request head`() {
        val s = scene { worktreeHead = "d".repeat(40) }
        assertThatThrownBy { cleanup(s) }.hasMessageContaining("not at the commit that was merged")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `nothing is deleted when the worktree moved to another branch`() {
        val s = scene { branch = "other" }
        assertThatThrownBy { cleanup(s) }.hasMessageContaining("is on other now")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `a dirty worktree aborts before anything is deleted`() {
        val s = scene { dirty = true }
        assertThatThrownBy { cleanup(s) }.hasMessageContaining("uncommitted changes")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `a plan whose commit changed after confirmation is refused`() {
        val s = scene()
        assertThatThrownBy { backend(s).inspectMergedWorktree(Path.of("wt"), github, "feature/x", "e".repeat(40)) }.hasMessageContaining("changed after the plan")
    }

    @Test
    fun `inspection reports head, main folder and ignored files`() {
        val merged = backend(scene()).inspectMergedWorktree(Path.of("wt"), github, "feature/x", null)
        assertThat(merged.head).isEqualTo(head)
        assertThat(merged.mainPath).isEqualTo(Path.of("main"))
        assertThat(merged.ignored).containsExactly(".env")
    }

    @Test
    fun `guard accepts only a merged pull request at the local head`() {
        assertThat(mergedCleanupProblem(pr(), "feature/x", head, listOf("main"))).isNull()
        assertThat(mergedCleanupProblem(pr(oid = head.uppercase()), "feature/x", head, listOf("main"))).isNull()
        assertThat(mergedCleanupProblem(null, "feature/x", head, listOf("main"))).contains("No pull request")
        assertThat(mergedCleanupProblem(pr(PullRequestState.OPEN), "feature/x", head, listOf("main"))).contains("not merged")
        assertThat(mergedCleanupProblem(pr(PullRequestState.CLOSED), "feature/x", head, listOf("main"))).contains("not merged")
        assertThat(mergedCleanupProblem(pr(oid = "d".repeat(40)), "feature/x", head, listOf("main"))).contains("not at the commit")
        assertThat(mergedCleanupProblem(pr(oid = ""), "feature/x", head, listOf("main"))).contains("not at the commit")
        assertThat(mergedCleanupProblem(pr(), "feature/x", null, listOf("main"))).contains("not at the commit")
    }

    @Test
    fun `guard never allows the base, default or main branches`() {
        for (name in listOf("main", "master", "develop")) assertThat(mergedCleanupProblem(pr(), name, head, emptyList())).contains("never removed")
        assertThat(mergedCleanupProblem(pr(), "release", head, listOf("release"))).contains("never removed")
        assertThat(mergedCleanupProblem(pr(base = "trunk"), "trunk", head, listOf("trunk"))).contains("never removed")
        assertThat(mergedCleanupProblem(pr(), null, head, emptyList())).contains("no branch")
    }

    @Test
    fun `confirm lists path, branch, forced deletion, sessions and the configuration names`() {
        val text = cleanupConfirmText(plan)
        assertThat(text).contains("wt", "feature/x", "squash", "sessions and runs are stopped", "2 run configuration(s): web (x), api (x)")
        assertThat(cleanupConfirmText(plan.copy(configurations = emptyList()))).contains("no run configurations")
    }

    @Test
    fun `confirm names ignored files that will be deleted`() {
        assertThat(cleanupConfirmText(plan.copy(ignored = listOf(".env", "build/")))).contains("Ignored files", ".env, build/")
        assertThat(cleanupConfirmText(plan)).doesNotContain("Ignored files")
    }

    @Test
    fun `ignored files note is capped`() {
        val note = ignoredFilesNote((1..12).map { "f$it" })
        assertThat(note).contains("f1", "f8", "and 4 more").doesNotContain("f9,")
        assertThat(ignoredFilesNote(emptyList())).isNull()
    }

    @Test
    fun `a branch inside its base is cleaned up without asking gh`() {
        val s = scene { lookupFails = true; pullRequest = null; ancestor = true }
        assertThat(cleanup(s, plan.copy(mergedInto = "main"))).isNull()
        assertThat(s.data.calls).containsExactly("remove from=main force=false", "delete feature/x force=true", "deleteConfigs")
    }

    @Test
    fun `local evidence is not trusted when the branch is no longer inside the base`() {
        val s = scene { lookupFails = true; ancestor = false }
        assertThatThrownBy { cleanup(s, plan.copy(mergedInto = "main")) }.hasMessageContaining("Could not check the pull request")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `inspection reports the base that contains the branch and skips gh`() {
        val s = scene { lookupFails = true; ancestor = true }
        assertThat(backend(s).inspectMergedWorktree(Path.of("wt"), github, "feature/x", null, listOf("release"), alsoMain = true).mergedInto).isEqualTo("release")
        assertThat(backend(scene()).inspectMergedWorktree(Path.of("wt"), github, "feature/x", null, listOf("release"), alsoMain = true).mergedInto).isNull()
    }

    @Test
    fun `local evidence still never allows a base branch`() {
        assertThat(mergedCleanupProblem(null, "main", head, emptyList(), "release")).contains("never removed")
        assertThat(mergedCleanupProblem(null, "release", head, listOf("release"), "release")).contains("never removed")
        assertThat(mergedCleanupProblem(null, "feature/x", head, listOf("main"), "main")).isNull()
    }

    @Test
    fun `confirm names the base for a local merge and the task record`() {
        assertThat(cleanupConfirmText(plan.copy(mergedInto = "develop"))).contains("feature/x is merged into develop.", "the task record of this worktree").doesNotContain("pull request")
        assertThat(cleanupConfirmText(plan)).contains("The pull request of feature/x is merged.")
    }

    @Test
    fun `a merged gitlab merge request at the local head allows the cleanup`() {
        val s = scene()
        assertThat(cleanup(s, plan.copy(remote = gitlab))).isNull()
        assertThat(s.data.calls).contains("remove from=main force=false", "delete feature/x force=true")
    }

    @Test
    fun `a gitlab merge request at another commit is refused with merge request wording`() {
        val s = scene { worktreeHead = "d".repeat(40) }
        assertThatThrownBy { cleanup(s, plan.copy(remote = gitlab)) }.hasMessageContaining("older merge request")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `a failed glab lookup names glab and aborts`() {
        val s = scene { lookupFails = true }
        assertThatThrownBy { cleanup(s, plan.copy(remote = gitlab)) }.hasMessageContaining("Could not check the merge request", "glab")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `without a known remote and local evidence the cleanup is refused`() {
        val s = scene()
        assertThatThrownBy { cleanup(s, plan.copy(remote = null)) }.hasMessageContaining("neither GitHub nor GitLab")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `a closed but not merged gitlab merge request is refused and nothing is removed`() {
        val s = scene { pullRequest = pr(PullRequestState.CLOSED) }
        assertThatThrownBy { cleanup(s, plan.copy(remote = gitlab)) }.hasMessageContaining("merge request of feature/x is not merged")
        assertThat(s.data.calls).isEmpty()
    }

    @Test
    fun `a short head sha prefix matches only the same commit`() {
        assertThat(mergedCleanupProblem(pr(oid = head.take(12)), "feature/x", head, listOf("main"), forge = Forge.GITLAB)).isNull()
        assertThat(mergedCleanupProblem(pr(oid = "b" + head.drop(1)), "feature/x", head, listOf("main"), forge = Forge.GITLAB)).contains("merge request")
    }

    @Test
    fun `confirm of a gitlab plan says merge request`() {
        assertThat(cleanupConfirmText(plan.copy(remote = gitlab))).contains("The merge request of feature/x is merged.")
    }
}
