package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.Worktree
import java.nio.file.Path

fun GitState.createWorktree(branch: String, base: String, target: Path) =
    act("Creating worktree $branch", refreshWorktrees = true, onSuccess = { _: Unit -> host.registerWorktree(target) }) { worktrees.add(repo, branch, base, target) }

/** Stops what runs in the folder first, then removes it (never its branch) and the project registered for it. */
fun GitState.removeWorktree(worktree: Worktree, force: Boolean) {
    if (!busy) host.stopWorktreeWork(worktree.path())
    act("Removing worktree ${worktreeTitle(worktree)}", refreshWorktrees = true, onSuccess = { _: Unit -> host.forgetWorktree(worktree.path()) }) {
        worktrees.remove(repo, worktree.path(), force)
    }
}

fun GitState.requestRemoveWorktree(worktree: Worktree) {
    dialog = GitDialog.RemoveWorktree(worktree)
}

fun GitState.openWorktree(worktree: Worktree, launchSession: Boolean) = host.openWorktree(worktree.path(), launchSession)
