package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.PullRequestForm
import com.github.krpkbhdn.ccmanager.ui.git.model.problem
import com.github.krpkbhdn.ccmanager.ui.git.model.reviewPullRequest
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val BODY_MIN_LINES = 6
private const val BODY_MAX_LINES = 14

/** Claude takes 30 to 90 seconds; Cancel (or Esc) stops waiting for it. */
@Composable
fun DraftingDialog(dialog: GitDialog.Drafting, onDismiss: () -> Unit) {
    val cancel = { dialog.job.cancel(); onDismiss() }
    ModalHost(cancel) {
        DialogBody(dialog.title, Icons.Push, colors.accent) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Spinner()
                Label("Drafting the title and description from the branch changes...", color = colors.textSecondary)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AppButton("Cancel", cancel) }
        }
    }
}

@Composable
fun PullRequestDialog(git: GitState, dialog: GitDialog.PullRequest, onDismiss: () -> Unit) {
    val form = dialog.form
    val title = rememberTextFieldState(form.title)
    val body = rememberTextFieldState(form.body)
    val base = rememberTextFieldState(form.base)
    fun edited() = form.copy(base = base.text.toString(), title = title.text.toString(), body = body.text.toString())
    val problem = edited().problem()
    val review = { if (problem == null && !git.busy) git.reviewPullRequest(edited()) }
    ModalHost(onDismiss, takeFocus = false, hasInput = { true }) {
        DialogBody("Create pull request", Icons.Push, colors.accent) {
            LabeledField("Title", problem?.takeIf { title.text.isBlank() }) { AppTextField(title, Modifier.fillMaxWidth(), onSubmit = review) }
            LabeledField("Description") { AppTextArea(body, Modifier.fillMaxWidth(), minLines = BODY_MIN_LINES, maxLines = BODY_MAX_LINES) }
            LabeledField("Base branch", problem?.takeIf { title.text.isNotBlank() }) { AppTextField(base, Modifier.fillMaxWidth(), mono = true, onSubmit = review) }
            LabeledField("Head branch") { Label(form.head, color = colors.textPrimary) }
            LabeledField("Remote") { Label(form.remote, color = colors.textPrimary) }
            DialogActions("Review", problem == null && !git.busy, false, review, onDismiss)
        }
    }
}
