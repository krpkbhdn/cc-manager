package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewComment
import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.git.model.reanchorReview
import com.github.krpkbhdn.ccmanager.ui.git.model.reviewLocation
import com.github.krpkbhdn.ccmanager.ui.git.model.reviewPrompt
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val listMaxHeight = 160.dp
private const val NEW_SESSION = ""
private const val SEND_FAILED_TITLE = "Review comments not sent"
private const val SEND_FAILED_BODY = "The session did not accept the prompt. The comments stay open."

/** Collapsible list of the project's review comments with the session picker and the Send button. */
@Composable
fun ReviewCommentsPanel(git: GitState, target: ReviewTarget, onSelectFile: (String) -> Unit, modifier: Modifier = Modifier) {
    val comments = git.review.all
    if (comments.isEmpty()) return
    var expanded by remember { mutableStateOf(true) }
    var picked by remember { mutableStateOf<String?>(null) }
    val sessions = target.sessions()
    val sessionId = listOf(picked, target.preferred()).firstOrNull { id -> sessions.any { it.ccmSessionId == id } } ?: sessions.firstOrNull()?.ccmSessionId
    val chosen = if (picked == NEW_SESSION && sessions.isNotEmpty()) null else sessionId
    val open = git.review.open()
    val sentCount = comments.count { it.status == ReviewStatus.SENT }
    Column(modifier.fillMaxWidth()) {
        HorizontalRule()
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            IconButton(if (expanded) Icons.ChevronDown else Icons.ChevronRight, { expanded = !expanded }, tooltip = if (expanded) "Collapse" else "Expand", size = 26.dp)
            Label("Review comments (${comments.size})", Modifier.weight(1f), color = colors.textSecondary, style = Type.smallMedium)
            SessionPicker(sessions, chosen) { picked = it }
            if (sentCount > 0) {
                AppButton("Re-check sent", { recheckSent(git) }, compact = true)
                AppButton("Reopen", { git.review.reopenSent() }, compact = true)
            }
            AppButton("Send to agent", { send(git, target, chosen, open); if (chosen == null) picked = null }, compact = true, enabled = open.isNotEmpty())
        }
        if (git.review.recheckOffered) RecheckBanner(git, sentCount)
        if (expanded) {
            Column(Modifier.heightIn(max = listMaxHeight).verticalScroll(rememberScrollState())) {
                comments.forEach { CommentRow(it, { onSelectFile(it.path) }, { git.review.remove(it.id) }) }
            }
        }
    }
}

private fun send(git: GitState, target: ReviewTarget, sessionId: String?, open: List<ReviewComment>) {
    if (open.isEmpty()) return
    val ids = open.map { it.id }.toSet()
    val prompt = reviewPrompt(open)
    if (sessionId == null) return target.startAndSend(prompt) { git.review.markSent(ids, it, null) }
    val id = target.send(sessionId, prompt)
    if (id == null) git.host.notify(NotificationKind.ERROR, SEND_FAILED_TITLE, SEND_FAILED_BODY) else git.review.markSent(ids, sessionId, id)
}

private fun recheckSent(git: GitState) {
    git.reanchorReview(true)
    git.review.clearRecheckOffer()
}

@Composable
private fun SessionPicker(sessions: List<SessionSnapshot>, selected: String?, onPick: (String) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        val current = sessions.firstOrNull { it.ccmSessionId == selected }
        AppButton(current?.let(::sessionTitle) ?: "New session", { open = true }, compact = true, enabled = sessions.isNotEmpty(), icon = Icons.ChevronDown)
        if (open) {
            val entries = sessions.map { MenuEntry(sessionTitle(it), status = it.status, onSelect = { onPick(it.ccmSessionId) }) }
            PopupMenu(entries + MenuEntry("New session", onSelect = { onPick(NEW_SESSION) }), { open = false }, alignEnd = true)
        }
    }
}

@Composable
private fun RecheckBanner(git: GitState, sentCount: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("Agent finished. Re-check $sentCount sent comments?", Modifier.weight(1f), style = Type.small)
        AppButton("Re-check", { recheckSent(git) }, compact = true)
    }
}

@Composable
private fun CommentRow(comment: ReviewComment, onSelect: () -> Unit, onDelete: () -> Unit) {
    Pressable(onSelect, Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(reviewLocation(comment), color = colors.textSecondary, style = Type.monoSmall)
            Label(comment.text, Modifier.weight(1f), style = Type.small, tooltipWhenTruncated = true)
            Chip(comment.status.name.lowercase(), statusTint(comment.status))
            IconButton(Icons.Trash, onDelete, tooltip = "Delete comment", size = 24.dp)
        }
    }
}

@Composable
private fun statusTint(status: ReviewStatus): Color = when (status) {
    ReviewStatus.OPEN -> colors.accent
    ReviewStatus.SENT -> colors.success
    ReviewStatus.STALE -> colors.danger
}
