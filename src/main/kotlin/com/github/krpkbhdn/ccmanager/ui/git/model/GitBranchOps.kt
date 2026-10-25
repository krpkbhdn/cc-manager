package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitMisc
import com.github.krpkbhdn.ccmanager.git.GitMisc.MergeMode
import com.github.krpkbhdn.ccmanager.git.GitMisc.ResetMode
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest

/** Switches branch; a dirty tree opens the "stash and switch" dialog instead of failing. */
fun GitState.checkout(branch: String, autoStash: Boolean = false) =
    act("Switching to $branch", handle = { e -> offerStash(e, branch) }) { branches.checkout(repo, branch, autoStash) }

private fun GitState.offerStash(e: GitException, branch: String): Boolean {
    if (e.kind() != GitException.Kind.DIRTY_TREE) return false
    dialog = GitDialog.DirtyCheckout(branch)
    return true
}

fun GitState.checkoutCommit(commitish: String) = act("Checking out ${commitish.take(SHORT_HASH)}") { branches.checkoutCommit(repo, commitish) }

fun GitState.createBranch(name: String, startPoint: String?, checkout: Boolean) =
    act("Creating $name") { branches.create(repo, name, startPoint, checkout) }

fun GitState.renameBranch(oldName: String, newName: String) = act("Renaming $oldName") { branches.rename(repo, oldName, newName) }

fun GitState.deleteBranch(name: String, force: Boolean) =
    act("Deleting $name", handle = { e -> offerForceDelete(e, name) }) { branches.delete(repo, name, force) }

private fun GitState.offerForceDelete(e: GitException, name: String): Boolean {
    if (!e.output().orEmpty().contains(NOT_MERGED_MARKER)) return false
    dialog = GitDialog.Confirm(ConfirmRequest("Branch not merged", "$name has commits that are not merged anywhere. Delete it anyway?", "Delete anyway") { deleteBranch(name, true) })
    return true
}

private const val NOT_MERGED_MARKER = "not fully merged"

fun GitState.deleteRemoteBranch(remote: String, name: String) = act("Deleting $remote/$name") { branches.deleteRemote(repo, remote, name) }

fun GitState.setUpstream(branch: String, upstream: String) = act("Setting upstream") { branches.setUpstream(repo, branch, upstream) }

fun GitState.merge(branch: String, mode: MergeMode) = act("Merging $branch") { branches.merge(repo, branch, mode) }

fun GitState.rebase(onto: String) = act("Rebasing onto $onto") { branches.rebase(repo, onto) }

fun GitState.requestRebase(onto: String) {
    val current = status?.branch() ?: "HEAD"
    dialog = GitDialog.Confirm(ConfirmRequest("Rebase onto $onto", "Replay the commits of $current on top of $onto? This rewrites $current.", "Rebase", destructive = false) { rebase(onto) })
}

fun GitState.reset(commit: String, mode: ResetMode) = act("Resetting") { history.reset(repo, commit, mode) }

fun GitState.revert(commit: String) = act("Reverting ${commit.take(SHORT_HASH)}") { history.revert(repo, commit) }

fun GitState.cherryPick(commit: String) = act("Cherry-picking ${commit.take(SHORT_HASH)}") { history.cherryPick(repo, commit) }

fun GitState.createTag(name: String, commit: String?, message: String) = act("Tagging $name") { history.createTag(repo, name, commit, message) }

fun GitState.showInLog(ref: String) {
    log.update { it.forBranch(ref) }
    show(GitSubView.LOG)
}

fun GitState.requestDeleteTag(name: String) {
    dialog = GitDialog.Confirm(ConfirmRequest("Delete tag", "Delete the local tag $name?", "Delete") { deleteTag(name) })
}

fun GitState.deleteTag(name: String) = act("Deleting tag $name") { history.deleteTag(repo, name) }

fun GitState.stashPush(message: String, includeUntracked: Boolean) = act("Stashing") { stashes.push(repo, message, includeUntracked) }

fun GitState.stashApply(stash: GitMisc.Stash) = act("Applying stash") { stashes.apply(repo, stash.index(), stash.hash()) }

fun GitState.stashPop(stash: GitMisc.Stash) = act("Popping stash") { stashes.pop(repo, stash.index(), stash.hash()) }

fun GitState.requestDropStash(stash: GitMisc.Stash) {
    dialog = GitDialog.Confirm(ConfirmRequest("Drop stash", "Delete stash@{${stash.index()}}? Its changes are lost.", "Drop") { stashDrop(stash) })
}

fun GitState.stashDrop(stash: GitMisc.Stash) = act("Dropping stash") { stashes.drop(repo, stash.index(), stash.hash()) }

suspend fun GitState.stashDiff(hash: String) = guarded("Could not read the stash") { stashes.show(repo, hash) }

const val SHORT_HASH = 7
