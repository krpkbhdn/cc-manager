package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest
import com.github.krpkbhdn.ccmanager.git.GitMisc.MergeMode
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.ui.state.ALL_CONFIRM_LINES
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.nio.file.Path
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

private const val PREPARE_FAILED = "Could not prepare the merge"
private const val MERGE_FAILED = "Could not merge the task branch"
private const val MAX_LISTED_MERGE_COMMITS = 200
private const val CHANGED_AFTER_CONFIRM = "The worktree or the folder of the base branch changed after the confirmation; review and merge again"

/** What the merge dialog edits; [branch] is fixed, [dirty] says the worktree has changes that get committed first. */
data class TaskMergeForm(val base: String, val branch: String, val commitMessage: String, val mergeTitle: String, val mergeBody: String, val dirty: Boolean)

fun defaultMergeTitle(branch: String, base: String, title: String): String = "Merge $branch into $base" + if (title.isBlank()) "" else ": ${title.trim()}"

fun TaskMergeForm.mergeMessage(): String = if (mergeBody.isBlank()) mergeTitle.trim() else "${mergeTitle.trim()}\n\n${mergeBody.trim()}"

fun TaskMergeForm.problem(): String? = when {
    dirty && commitMessage.isBlank() -> "Commit message is empty"
    mergeTitle.isBlank() -> "Merge title is empty"
    mergeTitle.any { it.isISOControl() } -> "Merge title must be a single line"
    safeBase(base) == null -> "'$base' is not a valid base branch"
    base == branch -> "The task branch cannot be merged into itself"
    else -> null
}

/** Everything the merge will do, read from git; [files] are the dirty files that get committed in [worktree] first. */
internal class TaskMergePlan(
    val form: TaskMergeForm,
    val worktree: Path,
    val mergeFolder: Path,
    val head: String?,
    val files: List<String>,
    val commits: List<String>,
)

/** Everything the confirm dialog promises, in the order it happens. */
fun taskMergeConfirmText(form: TaskMergeForm, worktree: Path, mergeFolder: Path, files: List<String>, commits: List<String>, activeFolders: List<Path>): String = buildString {
    if (activeFolders.isNotEmpty()) appendLine("WARNING: a session or run is active in ${activeFolders.joinToString()}; it may change files while this runs")
    secretFilesWarning(files)?.let(::appendLine)
    var step = 1
    if (files.isNotEmpty()) {
        appendLine("${step++}. In $worktree: git add the ${files.size} listed file(s) and commit them with the message: ${form.commitMessage.trim()}")
        files.forEach { appendLine("     $it") }
    }
    val shown = commits.take(MAX_LISTED_MERGE_COMMITS)
    val count = if (commits.size > MAX_LISTED_MERGE_COMMITS) "more than $MAX_LISTED_MERGE_COMMITS commit(s), newest $MAX_LISTED_MERGE_COMMITS shown" else "${commits.size} commit(s)"
    appendLine("$step. In $mergeFolder (where ${form.base} is checked out): git merge --no-ff ${form.branch}, bringing in $count${if (files.isNotEmpty()) " plus the commit above" else ""}:")
    shown.forEach { appendLine("     $it") }
    appendLine("Merge commit message:")
    appendLine(form.mergeMessage())
    append("Nothing is pushed.")
}

/** The base a task is merged into: the one it was created from, else the main folder's branch. Blocks on git. */
internal fun GitState.draftBase(): String =
    task?.baseBranch()?.let(::safeBase) ?: defaultBase(git.worktrees.list(repo).firstOrNull { it.main() }?.branch())

/** Staging and committing during a merge, rebase or cherry-pick, or over conflicted files, would commit conflict markers. */
internal fun GitBackend.refuseUnfinishedWork(worktree: Path, current: GitStatus) {
    if (current.operation() != GitStatus.Operation.NONE || branches.mergeInProgress(worktree) || current.changes().any { it.conflicted() }) {
        throw refusal("$worktree has a merge, rebase or cherry-pick in progress or unresolved conflicts; resolve and commit or abort it there first")
    }
}

/**
 * Reads, from git, every condition of the merge and throws a refusal naming the first that fails: the base is checked out
 * somewhere else, that folder is clean and not mid-operation, and the task branch still has something to bring in.
 */
internal fun GitBackend.inspectTaskMerge(worktree: Path, form: TaskMergeForm): TaskMergePlan {
    form.problem()?.let { throw refusal(it) }
    val mergeFolder = branches.worktreeOf(worktree, form.base).orElseThrow { refusal("Check out ${form.base} in the main folder first: it is not checked out in any worktree") }
    if (sameFolder(mergeFolder, worktree)) throw refusal("The task branch cannot be merged into itself")
    if (branches.mergeInProgress(mergeFolder)) throw refusal("$mergeFolder has a merge, rebase, cherry-pick or revert in progress; finish or abort it first")
    if (branches.hasTrackedChanges(mergeFolder)) throw refusal("$mergeFolder has uncommitted changes to tracked files; commit or stash them first")
    val current = status.status(worktree)
    if (current.branch() != form.branch) throw refusal("The worktree is on ${current.branch() ?: "a detached HEAD"} now, not ${form.branch}")
    refuseUnfinishedWork(worktree, current)
    val files = current.changes().map { it.path() }
    if (files.isNotEmpty() && form.commitMessage.isBlank()) throw refusal("Commit message is empty")
    if (files.isEmpty() && branches.isAncestor(worktree, form.branch, form.base)) throw refusal("Nothing to merge: ${form.branch} is already in ${form.base}")
    return TaskMergePlan(form, worktree, mergeFolder, current.head(), files, branches.commitsAhead(worktree, form.base, form.branch, MAX_LISTED_MERGE_COMMITS + 1))
}

/**
 * Re-inspects, refuses when anything differs from what was confirmed, commits the dirty files in the worktree and merges with
 * a merge commit in the folder of the base. A failed merge is aborted at once, so that folder is never left mid-merge.
 */
internal fun GitBackend.mergeTask(confirmed: TaskMergePlan, progress: (String) -> Unit): String {
    val now = inspectTaskMerge(confirmed.worktree, confirmed.form)
    if (now.head != confirmed.head || now.files.toSet() != confirmed.files.toSet() || !sameFolder(now.mergeFolder, confirmed.mergeFolder)) throw refusal(CHANGED_AFTER_CONFIRM)
    val form = confirmed.form
    if (confirmed.files.isNotEmpty()) {
        progress("Committing")
        index.stage(confirmed.worktree, confirmed.files)
        index.commit(confirmed.worktree, CommitRequest(form.commitMessage.trim(), false, false, false))
    }
    progress("Merging ${form.branch} into ${form.base}")
    try {
        branches.merge(confirmed.mergeFolder, form.branch, MergeMode.NO_FAST_FORWARD, form.mergeMessage())
    } catch (e: GitException) {
        throw abortLeftoverMerge(confirmed, e)
    }
    return form.base
}

private fun GitBackend.abortLeftoverMerge(plan: TaskMergePlan, failure: GitException): GitException {
    val folder = plan.mergeFolder
    if (!branches.mergeInProgress(folder)) {
        return if (plan.files.isEmpty()) failure else refusal("${failure.message}. Your changes were committed in the task worktree; ${plan.form.base} is unchanged")
    }
    val conflicted = status.status(folder).changes().filter { it.conflicted() }.map { it.path() }
    try {
        conflicts.abortOperation(folder)
    } catch (e: GitException) {
        return refusal("The merge stopped and could not be aborted (${e.message}); run `git merge --abort` in $folder")
    }
    val base = plan.form.base
    val what = if (conflicted.isEmpty()) "The merge failed: ${failure.message}" else "Merge conflict in: ${conflicted.joinToString()}"
    val kept = if (plan.files.isEmpty()) "" else " Your changes stay committed in the worktree."
    return refusal("$what. $base is unchanged; merge $base into the task branch in the worktree, resolve there, then merge again.$kept")
}

/** The merge button of a task worktree: drafts the commit and merge text in the background (cancel closes the dialog), then opens the form. */
fun GitState.startTaskMerge() {
    val branch = status?.branch() ?: return
    val dirty = status?.changes().orEmpty().isNotEmpty()
    val taskText = task?.task().orEmpty()
    val job = scope.launch(start = CoroutineStart.LAZY) {
        val drafted = gitCall({ fail(PREPARE_FAILED, it) }) {
            val base = draftBase()
            base to git.forge.draft(repo, base, taskText)
        }
        dialog = drafted?.let { (base, text) -> GitDialog.MergeTask(TaskMergeForm(base, branch, text.title(), defaultMergeTitle(branch, base, text.title()), text.body(), dirty)) }
    }
    dialog = GitDialog.Drafting("Merge into base branch", job)
    job.start()
}

fun GitState.reviewTaskMerge(form: TaskMergeForm) {
    if (form.problem() != null) return
    val worktree = repo
    scope.launch {
        val plan = gitCall({ fail(PREPARE_FAILED, it) }) { git.inspectTaskMerge(worktree, form) } ?: return@launch
        val active = listOf(plan.worktree, plan.mergeFolder).filter(host.hasRunningWork)
        dialog = GitDialog.Confirm(
            ConfirmRequest("Merge into ${form.base}", taskMergeConfirmText(form, plan.worktree, plan.mergeFolder, plan.files, plan.commits, active), "Merge", destructive = false, messageLines = ALL_CONFIRM_LINES) { runTaskMerge(plan) },
        )
    }
}

private fun GitState.runTaskMerge(plan: TaskMergePlan) =
    act("Merging ${plan.form.branch} into ${plan.form.base}", onSuccess = { base: String ->
        mergedInto = base
        success("Merged ${plan.form.branch} into $base", "Nothing was pushed")
        requestMergedCleanup()
    }) { progress -> mergeTask(plan, progress) }
