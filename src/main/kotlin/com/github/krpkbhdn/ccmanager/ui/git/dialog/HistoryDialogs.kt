package com.github.krpkbhdn.ccmanager.ui.git.dialog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitMisc.ResetMode
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.DialogActions
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.git.branch.branchNameProblem
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.SHORT_HASH
import com.github.krpkbhdn.ccmanager.ui.git.model.createTag
import com.github.krpkbhdn.ccmanager.ui.git.model.reset
import com.github.krpkbhdn.ccmanager.ui.git.model.stashPush
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val outputWidth = 640.dp
private val outputMaxHeight = 320.dp

@Composable
fun SaveStashDialog(git: GitState, onDismiss: () -> Unit) {
    val message = rememberTextFieldState()
    var untracked by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val submit = { onDismiss(); git.stashPush(message.text.toString().trim(), untracked) }
    ModalHost(onDismiss, takeFocus = false) {
        DialogBody("Stash changes", Icons.Archive, colors.accent) {
            LabeledField("Message (optional)") { AppTextField(message, Modifier.fillMaxWidth(), placeholder = "What is parked here", focusRequester = focus, onSubmit = submit) }
            CheckboxRow("Include untracked files", untracked, { untracked = it })
            DialogActions("Stash", true, false, submit, onDismiss)
        }
    }
}

@Composable
fun NewTagDialog(git: GitState, dialog: GitDialog.NewTag, onDismiss: () -> Unit) {
    val name = rememberTextFieldState()
    val message = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val problem = name.text.toString().takeIf { it.isNotEmpty() }?.let { branchNameProblem(it, git.tags.map { tag -> tag.name() }) }
    val valid = name.text.isNotBlank() && problem == null
    val submit = { if (valid) { onDismiss(); git.createTag(name.text.toString(), dialog.commit, message.text.toString().trim()) } }
    ModalHost(onDismiss, takeFocus = false) {
        DialogBody("New tag", Icons.Tag, colors.warning) {
            LabeledField("Name", problem) { AppTextField(name, Modifier.fillMaxWidth(), placeholder = "v1.2.0", focusRequester = focus, mono = true, onSubmit = submit) }
            LabeledField("Message (makes it an annotated tag)") { AppTextField(message, Modifier.fillMaxWidth(), placeholder = "Optional", onSubmit = submit) }
            Label("At ${dialog.commit?.take(SHORT_HASH) ?: "HEAD"}", color = colors.textTertiary, style = Type.caption)
            DialogActions("Create tag", valid, false, submit, onDismiss)
        }
    }
}

private val resetChoices = listOf(
    Choice(ResetMode.SOFT, "Soft", "Move the branch only; everything stays staged."),
    Choice(ResetMode.MIXED, "Mixed", "Move the branch and unstage; files on disk stay as they are."),
    Choice(ResetMode.HARD, "Hard", "Move the branch and throw away all uncommitted changes."),
)

@Composable
fun ResetDialog(git: GitState, dialog: GitDialog.Reset, onDismiss: () -> Unit) {
    var mode by remember { mutableStateOf(ResetMode.MIXED) }
    val hard = mode == ResetMode.HARD
    ModalHost(onDismiss) {
        DialogBody("Reset ${git.status?.branch() ?: "HEAD"}", Icons.Rewind, if (hard) colors.danger else colors.accent) {
            Label("To ${dialog.label}", color = colors.textSecondary, style = Type.small, maxLines = 2)
            ChoiceList(resetChoices, mode) { mode = it }
            if (hard) Label("Uncommitted work and commits after this point can be lost.", color = colors.danger, style = Type.small, maxLines = 2)
            DialogActions(if (hard) "Hard reset" else "Reset", true, hard, { onDismiss(); git.reset(dialog.commit, mode) }, onDismiss)
        }
    }
}

/** Long git output (a hook failure, a rejected push) in a scrollable monospace box with a copy button. */
@Composable
fun OutputDialog(dialog: GitDialog.Output, onDismiss: () -> Unit) {
    ModalHost(onDismiss, width = outputWidth) {
        DialogBody(dialog.title, Icons.Terminal, colors.danger) {
            val shape = RoundedCornerShape(Radius.md)
            Column(Modifier.fillMaxWidth().heightIn(max = outputMaxHeight).clip(shape).background(colors.terminal).verticalScroll(rememberScrollState()).padding(Spacing.md)) {
                Label(dialog.text, color = colors.textPrimary.copy(alpha = 1f - Alpha.FAINT), style = Type.monoSmall, maxLines = Int.MAX_VALUE)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm, Alignment.End)) {
                AppButton("Copy", { copyToClipboard(dialog.text) }, icon = Icons.Copy)
                AppButton("Close", onDismiss)
            }
        }
    }
}
