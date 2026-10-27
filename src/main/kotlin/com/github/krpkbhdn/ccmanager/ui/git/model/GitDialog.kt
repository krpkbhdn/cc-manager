package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest

/** A modal the Git tab is showing; [GitState] owns at most one at a time. */
sealed interface GitDialog {
    data class NewBranch(val startPoint: String?) : GitDialog
    data class RenameBranch(val name: String) : GitDialog
    data class DeleteBranch(val branch: GitBranch) : GitDialog
    data class Merge(val branch: String) : GitDialog
    data class SaveStash(val message: String = "") : GitDialog
    data class NewTag(val commit: String?) : GitDialog
    data class Reset(val commit: String, val label: String) : GitDialog
    data class DirtyCheckout(val branch: String) : GitDialog
    data object CreateWorktree : GitDialog
    data class RemoveWorktree(val worktree: Worktree) : GitDialog
    data class Confirm(val request: ConfirmRequest) : GitDialog
    data class Output(val title: String, val text: String) : GitDialog
}
