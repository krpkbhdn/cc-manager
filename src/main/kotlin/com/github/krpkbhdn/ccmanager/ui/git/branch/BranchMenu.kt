package com.github.krpkbhdn.ccmanager.ui.git.branch

import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.checkout
import com.github.krpkbhdn.ccmanager.ui.git.model.requestRebase
import com.github.krpkbhdn.ccmanager.ui.git.model.setUpstream
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.git.model.deleteRemoteBranch

/** The per-branch actions, shared by the popup, the Branches view and right-click menus. */
fun branchMenu(git: GitState, branch: GitBranch): List<MenuEntry> {
    val local = !branch.remote()
    val here = branch.current()
    val sameNameRemote = git.branches.firstOrNull { it.remote() && shortName(it.name()) == branch.name() }?.name()
    return buildList {
        if (!here) add(MenuEntry("Checkout", Icons.Branch) { git.checkout(branch.name()) })
        add(MenuEntry("New branch from here...", Icons.Plus) { git.dialog = GitDialog.NewBranch(branch.name()) })
        if (local) add(MenuEntry("Rename...", Icons.Edit) { git.dialog = GitDialog.RenameBranch(branch.name()) })
        if (!here) add(MenuEntry("Merge into current...", Icons.Merge, dividerAbove = true) { git.dialog = GitDialog.Merge(branch.name()) })
        if (!here) add(MenuEntry("Rebase current onto this...", Icons.Rewind) { git.requestRebase(branch.name()) })
        if (local && sameNameRemote != null && branch.upstream() != sameNameRemote) {
            add(MenuEntry("Track $sameNameRemote", Icons.Link) { git.setUpstream(branch.name(), sameNameRemote) })
        }
        add(MenuEntry("Show in log", Icons.Log, dividerAbove = true) { git.log.update { it.forBranch(branch.name()) }; git.show(GitSubView.LOG) })
        if (local && !here) add(MenuEntry("Delete...", Icons.Trash, danger = true, dividerAbove = true) { git.dialog = GitDialog.DeleteBranch(branch) })
        if (!local) add(MenuEntry("Delete remote branch...", Icons.Trash, danger = true, dividerAbove = true) { git.dialog = deleteRemoteDialog(git, branch) })
    }
}

private fun deleteRemoteDialog(git: GitState, branch: GitBranch): GitDialog.Confirm {
    val remote = remoteOf(branch.name())
    val name = shortName(branch.name())
    return GitDialog.Confirm(ConfirmRequest("Delete remote branch", "Delete $name on $remote? Others lose this branch.", "Delete") { git.deleteRemoteBranch(remote, name) })
}
