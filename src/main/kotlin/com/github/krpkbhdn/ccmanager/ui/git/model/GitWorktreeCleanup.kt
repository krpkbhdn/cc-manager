package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.github.PullRequest
import com.github.krpkbhdn.ccmanager.github.PullRequestState
import com.github.krpkbhdn.ccmanager.ui.state.ALL_CONFIRM_LINES
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.nio.file.Path
import kotlinx.coroutines.launch

private const val CLEANUP_FAILED = "Could not clean up the merged worktree"
private const val IGNORED_SHOWN = 8
private val NEVER_DELETED = setOf("main", "master", "develop")

/**
 * What removing a merged worktree touches; [mainPath] is where git runs, never inside [worktree]; [head] is the commit that was
 * checked; [mergedInto] is the branch that already contains it, null when a merged pull request is the evidence.
 */
data class CleanupPlan(
    val worktree: Path,
    val branch: String,
    val head: String,
    val mainPath: Path,
    val configurations: List<String>,
    val ignored: List<String> = emptyList(),
    val mergedInto: String? = null,
)

/** Ignored files are not changes, yet removing the folder deletes them: name them, capped. */
fun ignoredFilesNote(ignored: List<String>): String? =
    if (ignored.isEmpty()) null
    else "Ignored files that git does not track will be deleted too: ${ignored.take(IGNORED_SHOWN).joinToString()}" +
        if (ignored.size > IGNORED_SHOWN) " and ${ignored.size - IGNORED_SHOWN} more" else ""

fun cleanupConfirmText(plan: CleanupPlan): String = buildString {
    appendLine((plan.mergedInto?.let { "${plan.branch} is merged into $it." } ?: "The pull request of ${plan.branch} is merged.") + " This removes:")
    appendLine("- the worktree folder ${plan.worktree}; its running sessions and runs are stopped, and it must have no uncommitted changes")
    ignoredFilesNote(plan.ignored)?.let { appendLine("  $it") }
    appendLine("- the local branch ${plan.branch}, deleted with force: a squash or rebase merge leaves it not merged as far as git can tell")
    appendLine("- the task record of this worktree")
    if (plan.configurations.isEmpty()) {
        append("- no run configurations")
    } else {
        append("- ${plan.configurations.size} run configuration(s): ${plan.configurations.joinToString()}")
    }
    append("\nThe remote branch and the main project's own configurations stay.")
}

private fun sameCommit(a: String, b: String) = a.isNotEmpty() && b.isNotEmpty() && (a.startsWith(b, true) || b.startsWith(a, true))

/**
 * Why [branch] must not be removed as merged, or null when it may. [mergedInto] is local evidence (git found the branch inside
 * that base) and needs no gh; otherwise the pull request is looked up by branch name, so it only counts when its head commit
 * is exactly the local [head]: that also means the branch has nothing the pull request lacks.
 */
internal fun mergedCleanupProblem(pr: PullRequest?, branch: String?, head: String?, protectedBranches: Collection<String?>, mergedInto: String? = null): String? = when {
    branch.isNullOrBlank() -> "The worktree has no branch checked out"
    branch in NEVER_DELETED || branch in protectedBranches -> "$branch is a base branch and is never removed here"
    mergedInto != null -> null
    pr == null -> "No pull request was found for $branch"
    pr.state() != PullRequestState.MERGED -> "The pull request of $branch is not merged"
    !sameCommit(pr.headRefOid(), head.orEmpty()) -> "$branch is not at the commit that was merged (new commits, or an older pull request of the same name)"
    else -> null
}

/** Whether the toolbar offers the cleanup, from what is already known; the real check runs again before anything is deleted. */
fun GitState.mergedCleanupAvailable(): Boolean =
    mergedCleanupProblem(pullRequest, status?.branch(), status?.head(), listOf(pullRequest?.baseRef(), mergedInto), mergedInto) == null

internal class MergedWorktree(val head: String, val mainPath: Path, val ignored: List<String>, val mergedInto: String?)

private fun GitBackend.isMergedInto(repo: Path, branch: String, base: String) =
    try {
        branches.isAncestor(repo, branch, base)
    } catch (e: GitException) {
        false
    }

/**
 * Re-reads, from git and gh, everything the cleanup relies on and throws a [GitException] naming the first thing that does
 * not hold: it is a linked worktree on [branch], clean, whose branch is either inside one of [localBases] (or the main folder's
 * branch when [alsoMain]; null skips this route) or the merged head of a merged pull request, and not a base branch.
 * [expectedHead] must still be its commit when the user has already confirmed.
 */
internal fun GitBackend.inspectMergedWorktree(worktree: Path, branch: String, expectedHead: String?, localBases: List<String>? = null, alsoMain: Boolean = false): MergedWorktree {
    val entries = worktrees.list(worktree)
    val main = entries.first { it.main() }
    val self = entries.firstOrNull { !it.main() && sameFolder(it.path(), worktree) } ?: throw refusal("$worktree is not a linked worktree of this repository")
    if (self.branch() != branch) throw refusal("The worktree is on ${self.branch().ifBlank { "a detached HEAD" }} now, not $branch")
    val candidates = localBases?.let { if (alsoMain) it + main.branch() else it }.orEmpty().distinct().filter { it.isNotBlank() && it != branch }
    val mergedInto = candidates.firstOrNull { isMergedInto(main.path(), branch, it) }
    val pr = if (mergedInto != null) null else lookUpPullRequest(worktree, branch)
    mergedCleanupProblem(pr, self.branch(), self.head(), listOf(main.branch(), pr?.baseRef()) + candidates, mergedInto)?.let { throw refusal(it) }
    if (self.dirty()) throw refusal("The worktree has uncommitted changes; commit or discard them first")
    if (expectedHead != null && !sameCommit(expectedHead, self.head())) throw refusal("$branch changed after the plan was made; nothing was removed")
    return MergedWorktree(self.head(), main.path(), worktrees.ignoredEntries(worktree), mergedInto)
}

private fun GitBackend.lookUpPullRequest(worktree: Path, branch: String): PullRequest? {
    val lookup = github.lookup(worktree, branch)
    if (lookup.failed()) throw refusal("Could not check the pull request of $branch with gh; nothing was removed")
    return lookup.pullRequest().orElse(null)
}

/** Runs [block]; a git failure is rethrown saying which step it was and which steps had already completed. */
private inline fun <T> step(done: MutableList<String>, name: String, doneName: String, block: () -> T): T =
    try {
        block().also { done += doneName }
    } catch (e: GitException) {
        throw GitException(e.kind(), "$name failed: ${e.message}" + if (done.isEmpty()) " (nothing was changed)" else " (already done: ${done.joinToString()})", e.output().orEmpty())
    }

private fun GitBackend.deleteMergedBranch(plan: CleanupPlan) {
    val tip = branches.branches(plan.mainPath).firstOrNull { !it.remote() && it.name() == plan.branch } ?: return
    if (!sameCommit(tip.commit(), plan.head)) throw refusal("${plan.branch} moved after the pull request was merged, so it was kept")
    branches.delete(plan.mainPath, plan.branch, true)
}

/**
 * Re-checks the guards, removes the worktree (one retry, as Windows may still hold the folder; skipped when it is already
 * gone), force-deletes the branch only while it is still at the checked commit, and deletes the run configurations.
 * Sessions must already be stopped. Once the worktree is gone a failing branch deletion does not stop the rest: its message is
 * returned so the caller can forget the project and report it. [pause] is injected so tests do not sleep.
 */
internal fun GitBackend.cleanupMergedWorktree(host: GitHost, plan: CleanupPlan, progress: (String) -> Unit, pause: (Long) -> Unit = Thread::sleep): String? {
    val done = mutableListOf<String>()
    progress("Checking the worktree")
    if (worktrees.list(plan.mainPath).any { sameFolder(it.path(), plan.worktree) }) {
        inspectMergedWorktree(plan.worktree, plan.branch, plan.head, plan.mergedInto?.let(::listOf))
        progress("Removing worktree")
        step(done, "Removing the worktree", "removed the worktree") { removeWorktreeRetrying(plan.mainPath, plan.worktree, false, pause) }
    } else {
        done += "the worktree was already removed"
    }
    progress("Deleting branch")
    val failure = try {
        step(done, "Deleting the branch ${plan.branch}", "deleted the branch") { deleteMergedBranch(plan) }
        null
    } catch (e: GitException) {
        e.message
    }
    progress("Deleting run configurations")
    host.forgetStoredWorktree(plan.worktree)
    host.deleteRunConfigurations(plan.worktree)
    return failure
}

/** Builds the plan off the UI thread, then asks for confirmation; nothing happens before the user agrees. */
fun GitState.requestMergedCleanup() {
    val branch = status?.branch() ?: return
    val worktree = repo
    val localBases = listOfNotNull(mergedInto, task?.baseBranch())
    scope.launch {
        val plan = gitCall({ fail(CLEANUP_FAILED, it) }) {
            val merged = git.inspectMergedWorktree(worktree, branch, null, localBases, alsoMain = true)
            CleanupPlan(worktree, branch, merged.head, merged.mainPath, host.runConfigurationNames(worktree), merged.ignored, merged.mergedInto)
        } ?: return@launch
        dialog = GitDialog.Confirm(
            ConfirmRequest("Remove merged worktree", cleanupConfirmText(plan), "Remove worktree and branch", messageLines = ALL_CONFIRM_LINES) { cleanupMerged(plan) },
        )
    }
}

/** Verifies first so a refusal kills no session, then stops the work on the UI thread, then removes everything off it. */
private fun GitState.cleanupMerged(plan: CleanupPlan) {
    if (refuseWhenBusy()) return
    scope.launch {
        gitCall({ fail(CLEANUP_FAILED, it) }) { git.inspectMergedWorktree(plan.worktree, plan.branch, plan.head, plan.mergedInto?.let(::listOf)) } ?: return@launch
        if (refuseWhenBusy()) return@launch
        if (!stopWorkOrFail(plan.worktree, CLEANUP_FAILED)) return@launch
        val vanishing = leaveVanishingFolder(plan.worktree, plan.mainPath)
        act("Removing merged worktree ${plan.branch}", refreshWorktrees = true, folderVanishes = vanishing, onSuccess = { branchFailure: String? ->
            host.forgetWorktree(plan.worktree)
            branchFailure?.let { fail(CLEANUP_FAILED, refusal(it)) }
        }) { progress -> cleanupMergedWorktree(host, plan, progress) }
    }
}
