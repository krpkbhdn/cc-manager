package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.runtime.Composable
import com.github.krpkbhdn.ccmanager.ui.components.ConfirmDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState

/** Shows whichever dialog [GitState.dialog] holds; every dialog closes by clearing it. */
@Composable
fun GitDialogHost(git: GitState) {
    val dialog = git.dialog ?: return
    val close = { git.dialog = null }
    when (dialog) {
        is GitDialog.NewBranch -> NewBranchDialog(git, dialog, close)
        is GitDialog.RenameBranch -> RenameBranchDialog(git, dialog, close)
        is GitDialog.DeleteBranch -> DeleteBranchDialog(git, dialog, close)
        is GitDialog.Merge -> MergeDialog(git, dialog, close)
        is GitDialog.SaveStash -> SaveStashDialog(git, close)
        is GitDialog.NewTag -> NewTagDialog(git, dialog, close)
        is GitDialog.Reset -> ResetDialog(git, dialog, close)
        is GitDialog.DirtyCheckout -> DirtyCheckoutDialog(git, dialog, close)
        is GitDialog.CreateWorktree -> CreateWorktreeDialog(git, close)
        is GitDialog.RemoveWorktree -> RemoveWorktreeDialog(git, dialog, close)
        is GitDialog.Confirm -> ConfirmDialog(dialog.request, close)
        is GitDialog.Output -> OutputDialog(dialog, close)
    }
}
