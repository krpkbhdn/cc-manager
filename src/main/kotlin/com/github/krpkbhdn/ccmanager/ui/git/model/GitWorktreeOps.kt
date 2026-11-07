package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.FolderIdentity
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import java.nio.file.Path
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val STOP_POLLS = 20
private const val STOP_POLL_MILLIS = 250L
private const val UNLOCK_RETRY_MILLIS = 1_500L
private const val REMOVE_FAILED = "Could not remove the worktree"

fun GitState.createWorktree(branch: String, base: String, target: Path) =
    act("Creating worktree $branch", refreshWorktrees = true, onSuccess = { _: Any? -> host.showWorktree(projectId, target, false) }) { worktrees.add(repo, branch, base, target) }

/** The main worktree is where git must run to remove a linked one or its branch. */
internal fun GitState.mainWorktreePath(): Path = git.worktrees.list(repo).first { it.main() }.path()

internal fun refusal(message: String) = GitException(GitException.Kind.OTHER, message, "")

internal fun sameFolder(a: Path, b: Path) = FolderIdentity.same(a, b)

/** Dirtiness is read from git itself, so a tree edited after the dialog was drawn is still caught. */
internal fun GitBackend.requireClean(worktree: Path) {
    if (worktrees.list(worktree).any { sameFolder(it.path(), worktree) && it.dirty() }) {
        throw refusal("The worktree has uncommitted changes; commit or discard them first")
    }
}

/** Closes the sessions, shells and runs of the folder on the UI thread and waits, without blocking it, until they and their processes are gone; false when they are not within the bound. */
internal suspend fun GitHost.stopWorktreeWorkAndWait(directory: Path): Boolean {
    stopWorktreeWork(directory)
    var polls = 0
    while ((hasRunningWork(directory) || workStopping()) && polls++ < STOP_POLLS) delay(STOP_POLL_MILLIS)
    return !hasRunningWork(directory) && !workStopping()
}

/** [stopWorktreeWorkAndWait], reporting under [label] that nothing was removed when the work would not end. */
internal suspend fun GitState.stopWorkOrFail(directory: Path, label: String): Boolean =
    host.stopWorktreeWorkAndWait(directory).also { if (!it) fail(label, refusal("Sessions or processes in the folder did not end in time, so it is still in use; nothing was removed")) }

/** Removes the linked worktree from the main folder; Windows may still hold the folder for a moment, so one retry. [pause] is injected so tests do not sleep. */
internal fun GitBackend.removeWorktreeRetrying(mainPath: Path, worktree: Path, force: Boolean, pause: (Long) -> Unit = Thread::sleep) {
    try {
        worktrees.remove(mainPath, worktree, force)
    } catch (e: GitException) {
        pause(UNLOCK_RETRY_MILLIS)
        worktrees.remove(mainPath, worktree, force)
    }
}

internal fun GitState.refuseWhenBusy(): Boolean = busy.also { if (it) host.notify(NotificationKind.INFO, GIT_BUSY, "Wait for the running git operation to finish") }

/**
 * A state showing the folder that is about to vanish hands the view to the main folder first (that also releases its watcher)
 * and must not reload afterwards; returns whether it did.
 */
internal fun GitState.leaveVanishingFolder(doomed: Path, mainPath: Path): Boolean =
    sameFolder(repo, doomed).also { if (it) host.showWorktree(projectId, mainPath, false) }

/**
 * Checks (off the UI thread) that the folder is clean unless [force], stops what runs in it, checks again (a session may have
 * written meanwhile; a refusal then has already stopped the sessions) and removes it, always from the main folder, and the
 * project registered for it. The branch goes only
 * with [deleteBranch], forced when git does not consider it merged. The folder is gone even when the branch cannot be deleted,
 * so the worktree and the run configurations are forgotten anyway and the branch failure is reported afterwards.
 */
fun GitState.removeWorktree(worktree: Worktree, force: Boolean, deleteBranch: Boolean = false) {
    if (refuseWhenBusy()) return
    scope.launch {
        val main = gitCall({ fail(REMOVE_FAILED, it) }) {
            if (!force) git.requireClean(worktree.path())
            mainWorktreePath()
        } ?: return@launch
        if (refuseWhenBusy()) return@launch
        if (!stopWorkOrFail(worktree.path(), REMOVE_FAILED)) return@launch
        val vanishing = leaveVanishingFolder(worktree.path(), main)
        act("Removing worktree ${worktreeTitle(worktree)}", refreshWorktrees = true, folderVanishes = vanishing, onSuccess = { branchFailure: String? ->
            host.forgetWorktree(worktree.path())
            branchFailure?.let { fail(REMOVE_FAILED, refusal(it)) }
        }) {
            if (!force) requireClean(worktree.path())
            removeWorktreeRetrying(main, worktree.path(), force, host.unlockPause)
            val failure = if (deleteBranch) deleteBranchAfterRemoval(main, worktree) else null
            host.forgetStoredWorktree(worktree.path())
            host.deleteRunConfigurations(worktree.path())
            failure
        }
    }
}

private fun GitBackend.deleteBranchAfterRemoval(main: Path, worktree: Worktree): String? =
    try {
        branches.delete(main, worktree.branch(), !worktree.merged())
        null
    } catch (e: GitException) {
        "The worktree was removed, but deleting the branch ${worktree.branch()} failed: ${e.message}"
    }

fun GitState.requestRemoveWorktree(worktree: Worktree) {
    dialog = GitDialog.RemoveWorktree(worktree)
}

fun GitState.openWorktree(worktree: Worktree, launchSession: Boolean) = host.showWorktree(projectId, worktree.path(), launchSession)
