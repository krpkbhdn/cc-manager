package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.selectAll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.github.krpkbhdn.ccmanager.git.GitMisc.MergeMode
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.git.branch.branchNameProblem
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.checkout
import com.github.krpkbhdn.ccmanager.ui.git.model.createBranch
import com.github.krpkbhdn.ccmanager.ui.git.model.deleteBranch
import com.github.krpkbhdn.ccmanager.ui.git.model.merge
import com.github.krpkbhdn.ccmanager.ui.git.model.renameBranch
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

@Composable
fun NewBranchDialog(git: GitState, dialog: GitDialog.NewBranch, onDismiss: () -> Unit) {
    val name = rememberTextFieldState()
    val start = rememberTextFieldState(dialog.startPoint ?: git.status?.branch() ?: "HEAD")
    var checkout by remember { mutableStateOf(true) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val problem = name.text.toString().takeIf { it.isNotEmpty() }?.let { branchNameProblem(it, git.branches.filter { b -> !b.remote() }.map { b -> b.name() }) }
    val valid = name.text.isNotBlank() && problem == null && start.text.isNotBlank()
    val submit = { if (valid) { onDismiss(); git.createBranch(name.text.toString(), start.text.toString(), checkout) } }
    ModalHost(onDismiss, takeFocus = false) {
        DialogBody("New branch", Icons.Branch, colors.accent) {
            LabeledField("Name", problem) { AppTextField(name, Modifier.fillMaxWidth(), placeholder = "feature/my-change", focusRequester = focus, mono = true, onSubmit = submit) }
            LabeledField("Start point") { AppTextField(start, Modifier.fillMaxWidth(), placeholder = "branch, tag or commit", mono = true, onSubmit = submit) }
            CheckboxRow("Switch to the new branch", checkout, { checkout = it })
            DialogActions("Create", valid, false, submit, onDismiss)
        }
    }
}

@Composable
fun RenameBranchDialog(git: GitState, dialog: GitDialog.RenameBranch, onDismiss: () -> Unit) {
    val name = rememberTextFieldState(dialog.name)
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus(); name.edit { selectAll() } }
    val others = git.branches.filter { !it.remote() && it.name() != dialog.name }.map { it.name() }
    val problem = name.text.toString().takeIf { it != dialog.name }?.let { branchNameProblem(it, others) }
    val valid = name.text.isNotBlank() && name.text.toString() != dialog.name && problem == null
    val submit = { if (valid) { onDismiss(); git.renameBranch(dialog.name, name.text.toString()) } }
    ModalHost(onDismiss, takeFocus = false) {
        DialogBody("Rename branch", Icons.Edit, colors.accent) {
            LabeledField("New name for ${dialog.name}", problem) { AppTextField(name, Modifier.fillMaxWidth(), focusRequester = focus, mono = true, onSubmit = submit) }
            DialogActions("Rename", valid, false, submit, onDismiss)
        }
    }
}

@Composable
fun DeleteBranchDialog(git: GitState, dialog: GitDialog.DeleteBranch, onDismiss: () -> Unit) {
    var force by remember { mutableStateOf(false) }
    val unmerged = dialog.branch.ahead() > 0 && dialog.branch.upstream() == null
    ModalHost(onDismiss) {
        DialogBody("Delete branch", Icons.Trash, colors.danger) {
            Label("Delete ${dialog.branch.name()}?", color = colors.textPrimary)
            if (unmerged) Label("It has commits that exist nowhere else.", color = colors.warning, style = Type.small)
            CheckboxRow("Force delete", force, { force = it }, hint = "Delete even if the branch is not fully merged.")
            DialogActions("Delete", true, true, { onDismiss(); git.deleteBranch(dialog.branch.name(), force) }, onDismiss)
        }
    }
}

private val mergeChoices = listOf(
    Choice(MergeMode.DEFAULT, "Merge", "Fast-forward when possible, otherwise a merge commit."),
    Choice(MergeMode.NO_FAST_FORWARD, "Always create a merge commit", "Keeps the branch visible in history (--no-ff)."),
    Choice(MergeMode.FAST_FORWARD_ONLY, "Fast-forward only", "Fails instead of creating a merge commit (--ff-only)."),
    Choice(MergeMode.SQUASH, "Squash", "Stages all changes as one; you commit them yourself."),
)

@Composable
fun MergeDialog(git: GitState, dialog: GitDialog.Merge, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(MergeMode.DEFAULT) }
    ModalHost(onDismiss) {
        DialogBody("Merge into ${git.status?.branch() ?: "HEAD"}", Icons.Merge, colors.accent) {
            Label("Bring ${dialog.branch} into the current branch.", color = colors.textSecondary)
            ChoiceList(mergeChoices, mode) { mode = it }
            DialogActions("Merge", true, false, { onDismiss(); git.merge(dialog.branch, mode) }, onDismiss)
        }
    }
}

@Composable
fun DirtyCheckoutDialog(git: GitState, dialog: GitDialog.DirtyCheckout, onDismiss: () -> Unit) {
    ModalHost(onDismiss) {
        DialogBody("Local changes in the way", Icons.Alert, colors.warning) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("Switching to ${dialog.branch} would overwrite your uncommitted changes.", color = colors.textPrimary, maxLines = 3)
                Label("Stash them, switch, and put them back on the new branch?", color = colors.textSecondary, maxLines = 3)
            }
            DialogActions("Stash changes and switch", true, false, { onDismiss(); git.checkout(dialog.branch, autoStash = true) }, onDismiss)
        }
    }
}
