package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextArea
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.SUBJECT_HARD_LIMIT
import com.github.krpkbhdn.ccmanager.ui.git.model.SUBJECT_SOFT_LIMIT
import com.github.krpkbhdn.ccmanager.ui.git.model.SubjectLevel
import com.github.krpkbhdn.ccmanager.ui.git.model.canCommit
import com.github.krpkbhdn.ccmanager.ui.git.model.commit
import com.github.krpkbhdn.ccmanager.ui.git.model.lastCommitMessage
import com.github.krpkbhdn.ccmanager.ui.git.model.subjectHint
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.launch

/** Message field, amend / sign-off / skip-hooks options and the Commit buttons; Ctrl+Enter in the field commits. */
@Composable
fun CommitBox(git: GitState, stagedCount: Int, conflicts: Int, modifier: Modifier = Modifier) {
    val draft = git.draft
    val scope = rememberCoroutineScope()
    val focus = remember { FocusRequester() }
    LaunchedEffect(git.commitFocusTick) { if (git.takeCommitFocusRequest()) focus.requestFocus() }
    val message = draft.message.text.toString()
    val identityMissing = git.identity?.let { it.name().isNullOrBlank() || it.email().isNullOrBlank() } == true
    val ready = canCommit(message, stagedCount, draft.amend, git.busy, conflicts) && !identityMissing
    val commit = { thenPush: Boolean -> if (ready) git.commit(draft.request(), thenPush) { draft.clear() } }
    Column(modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        AppTextArea(draft.message, Modifier.fillMaxWidth(), placeholder = if (draft.amend) "Amend message" else "Message (Ctrl+Enter to commit)", focusRequester = focus, onSubmit = { commit(false) })
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            AppCheckbox(draft.amend, { on -> scope.launch { draft.setAmend(on, if (on) git.lastCommitMessage() else null) } })
            Label("Amend", color = colors.textSecondary, style = Type.small)
            OptionsMenu(git)
            Box(Modifier.weight(1f))
            SubjectCounter(message)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            AppButton(if (draft.amend) "Amend" else "Commit", { commit(false) }, Modifier.weight(1f), ButtonVariant.PRIMARY, Icons.Commit, ready, loading = git.job?.label == "Committing")
            AppButton("Commit & Push", { commit(true) }, variant = ButtonVariant.SECONDARY, icon = Icons.Push, enabled = ready)
        }
        if (identityMissing) Label("Set user.name and user.email first (git config).", color = colors.warning, style = Type.caption, maxLines = 2)
        if (conflicts > 0) Label("Resolve $conflicts conflicted ${if (conflicts == 1) "file" else "files"} first.", color = colors.warning, style = Type.caption)
    }
}

@Composable
private fun OptionsMenu(git: GitState) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(Icons.More, { open = true }, Modifier.padding(start = Spacing.xs), tooltip = "Commit options", size = 24.dp, active = git.draft.signOff || git.draft.skipHooks)
        if (open) {
            PopupMenu(
                listOf(
                    MenuEntry("Sign off", if (git.draft.signOff) Icons.Check else null, hint = "-s") { git.draft.signOff = !git.draft.signOff },
                    MenuEntry("Skip hooks", if (git.draft.skipHooks) Icons.Check else null, hint = "--no-verify") { git.draft.skipHooks = !git.draft.skipHooks },
                ),
                onDismiss = { open = false },
            )
        }
    }
}

@Composable
private fun SubjectCounter(message: String) {
    val hint = subjectHint(message)
    if (hint.length == 0) return
    val tint = when (hint.level) {
        SubjectLevel.OK -> colors.textTertiary
        SubjectLevel.LONG -> colors.warning
        SubjectLevel.TOO_LONG -> colors.danger
    }
    val limit = if (hint.level == SubjectLevel.OK) SUBJECT_SOFT_LIMIT else SUBJECT_HARD_LIMIT
    Label("${hint.length}/$limit", color = tint, style = Type.numericCaption)
}
