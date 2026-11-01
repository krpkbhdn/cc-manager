package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.problem
import com.github.krpkbhdn.ccmanager.ui.git.model.reviewTaskMerge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val BODY_MIN_LINES = 4
private const val BODY_MAX_LINES = 12

/** Local merge of a task branch into its base: nothing here pushes. */
@Composable
fun MergeTaskDialog(git: GitState, dialog: GitDialog.MergeTask, onDismiss: () -> Unit) {
    val form = dialog.form
    val commit = rememberTextFieldState(form.commitMessage)
    val title = rememberTextFieldState(form.mergeTitle)
    val body = rememberTextFieldState(form.mergeBody)
    val base = rememberTextFieldState(form.base)
    fun edited() = form.copy(base = base.text.toString(), commitMessage = commit.text.toString(), mergeTitle = title.text.toString(), mergeBody = body.text.toString())
    val problem = edited().problem()
    val review = { if (problem == null && !git.busy) git.reviewTaskMerge(edited()) }
    ModalHost(onDismiss, takeFocus = false, hasInput = { true }) {
        DialogBody("Merge into ${form.base}", Icons.Merge, colors.accent) {
            val commitMissing = form.dirty && commit.text.isBlank()
            if (form.dirty) LabeledField("Commit message for the uncommitted changes", problem?.takeIf { commitMissing }) { AppTextField(commit, Modifier.fillMaxWidth(), onSubmit = review) }
            LabeledField("Merge commit title", problem?.takeIf { !commitMissing }) { AppTextField(title, Modifier.fillMaxWidth(), onSubmit = review) }
            LabeledField("Merge commit description") { AppTextArea(body, Modifier.fillMaxWidth(), minLines = BODY_MIN_LINES, maxLines = BODY_MAX_LINES) }
            LabeledField("Merge into (base branch)") { AppTextField(base, Modifier.fillMaxWidth(), mono = true, onSubmit = review) }
            LabeledField("Task branch") { Label(form.branch, color = colors.textPrimary) }
            DialogActions("Review", problem == null && !git.busy, false, review, onDismiss)
        }
    }
}
