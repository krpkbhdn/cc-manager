package com.github.krpkbhdn.ccmanager.ui.git.log

import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.checkoutCommit
import com.github.krpkbhdn.ccmanager.ui.git.model.cherryPick
import com.github.krpkbhdn.ccmanager.ui.git.model.revert
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest

/** Right-click actions of one commit. */
fun commitMenu(git: GitState, commit: GitLog.Commit): List<MenuEntry> = listOf(
    MenuEntry("Checkout (detached)", Icons.Branch) { git.checkoutCommit(commit.hash()) },
    MenuEntry("New branch here...", Icons.Plus) { git.dialog = GitDialog.NewBranch(commit.hash()) },
    MenuEntry("New tag here...", Icons.Tag) { git.dialog = GitDialog.NewTag(commit.hash()) },
    MenuEntry("Cherry-pick", Icons.Commit, dividerAbove = true) { git.confirmOnCurrentBranch("Cherry-pick", "Apply", commit) { git.cherryPick(commit.hash()) } },
    MenuEntry("Revert", Icons.Undo) { git.confirmOnCurrentBranch("Revert", "Undo", commit) { git.revert(commit.hash()) } },
    MenuEntry("Reset current branch to here...", Icons.Rewind, danger = true) { git.dialog = GitDialog.Reset(commit.hash(), "${commit.shortHash()} ${commit.subject()}") },
    MenuEntry("Copy hash", Icons.Copy, dividerAbove = true) { copyToClipboard(commit.hash()) },
)

private fun GitState.confirmOnCurrentBranch(action: String, verb: String, commit: GitLog.Commit, run: () -> Unit) {
    dialog = GitDialog.Confirm(ConfirmRequest(action, "$verb ${commit.shortHash()} ${commit.subject()} on the current branch? This adds a commit.", action, destructive = false) { run() })
}
