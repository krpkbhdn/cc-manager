package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.github.krpkbhdn.ccmanager.git.WorktreeNames
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.createWorktree
import com.github.krpkbhdn.ccmanager.ui.git.model.defaultWorktreePath
import com.github.krpkbhdn.ccmanager.ui.git.model.refilledPath
import com.github.krpkbhdn.ccmanager.ui.git.model.removalNeedsForce
import com.github.krpkbhdn.ccmanager.ui.git.model.removeWorktree
import com.github.krpkbhdn.ccmanager.ui.git.model.worktreeTitle
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.nio.file.InvalidPathException
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val INVALID_PATH = "Not a valid path"
private const val REMOVE_MESSAGE_LINES = 4

private fun pathProblem(text: String): String? =
    try {
        WorktreeNames.pathProblem(Path.of(text)).orElse(null)
    } catch (e: InvalidPathException) {
        INVALID_PATH
    }

/** The folder check of [text]; it counts only while [text] is still what the field holds. */
private class PathCheck(val text: String, val problem: String?)

@Composable
fun CreateWorktreeDialog(git: GitState, onDismiss: () -> Unit) {
    val branch = rememberTextFieldState()
    val base = rememberTextFieldState(git.status?.branch() ?: "HEAD")
    val path = rememberTextFieldState()
    var suggested by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val branchText = branch.text.toString()
    val branchProblem = branchText.takeIf { it.isNotEmpty() }?.let { WorktreeNames.branchProblem(it).orElse(null) }
    LaunchedEffect(branchText) {
        if (branchText.isBlank() || branchProblem != null) return@LaunchedEffect
        val next = defaultWorktreePath(git.repo, branchText)
        path.setTextAndPlaceCursorAtEnd(refilledPath(path.text.toString(), suggested, next))
        suggested = next
    }
    val pathText = path.text.toString()
    val check by produceState(PathCheck("", null), pathText) {
        value = PathCheck(pathText, if (pathText.isEmpty()) null else withContext(Dispatchers.IO) { pathProblem(pathText) })
    }
    val pathPending = check.text != pathText
    val pathError = if (pathPending) null else check.problem
    val valid = branchText.isNotBlank() && branchProblem == null && base.text.isNotBlank() && pathText.isNotBlank() && !pathPending && pathError == null && !git.busy
    val submit = { if (valid) { onDismiss(); git.createWorktree(branchText, base.text.toString(), Path.of(pathText)) } }
    ModalHost(onDismiss, takeFocus = false, hasInput = { branchText.isNotEmpty() }) {
        DialogBody("Create worktree", Icons.FolderTree, colors.accent) {
            LabeledField("Branch", branchProblem) { AppTextField(branch, Modifier.fillMaxWidth(), placeholder = "feature/my-change", focusRequester = focus, mono = true, onSubmit = submit) }
            LabeledField("Base") { AppTextField(base, Modifier.fillMaxWidth(), placeholder = "branch, tag or commit", mono = true, onSubmit = submit) }
            LabeledField("Folder", pathError) { AppTextField(path, Modifier.fillMaxWidth(), placeholder = "where the worktree goes", mono = true, onSubmit = submit) }
            DialogActions("Create", valid, false, submit, onDismiss)
        }
    }
}

@Composable
fun RemoveWorktreeDialog(git: GitState, dialog: GitDialog.RemoveWorktree, onDismiss: () -> Unit) {
    var fresh by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { git.loadWorktrees(); fresh = true }
    val worktree = git.worktrees.firstOrNull { it.path() == dialog.worktree.path() } ?: dialog.worktree
    val needsForce = removalNeedsForce(worktree)
    val running = remember(worktree) { git.host.hasRunningWork(worktree.path()) }
    var discard by remember { mutableStateOf(false) }
    val valid = fresh && (!needsForce || discard) && !git.busy
    ModalHost(onDismiss) {
        DialogBody("Remove worktree", Icons.Trash, colors.danger) {
            Label("Remove the folder ${worktree.path()} of ${worktreeTitle(worktree)}? The branch and its commits stay; the project registered for this folder is removed.", color = colors.textSecondary, maxLines = REMOVE_MESSAGE_LINES)
            if (running) {
                Label("Its running sessions will be terminated first.", color = colors.danger, style = Type.bodyMedium, maxLines = 2)
            }
            if (needsForce) {
                Label("This worktree has uncommitted changes. They will be lost.", color = colors.danger, style = Type.bodyMedium, maxLines = 2)
                CheckboxRow("Discard the uncommitted changes", discard, { discard = it })
            }
            DialogActions("Remove", valid, true, { onDismiss(); git.removeWorktree(worktree, needsForce) }, onDismiss)
        }
    }
}
