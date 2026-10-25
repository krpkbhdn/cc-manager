package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.extensions.MonoBlock
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val COLLAPSED_LINES = 12
private const val COLLAPSED_CHARS = 2000
private const val HINTS = "Tab moves · Tab to Deny · Ctrl+Enter allows"

/** Any tool call that needs approval: what it does, optional "remember" choices from Claude's own suggestions, and a reason for denying. Focus starts on the reason, so a stray Enter never allows. */
@Composable
internal fun PermissionDialog(proposal: PermissionProposal, title: FrameTitle, context: ProposalContext) {
    val draft = remember { context.draft { PermissionDraft(proposal) } }
    val focus = remember { FocusRequester() }
    ProposalDialogFrame(
        title, context, PERMISSION_WIDTH, focus, HINTS,
        actions = {
            DialogButton("Deny", { context.answer(draft.deny()) })
            DialogButton("Allow", { context.answer(draft.allow()) }, variant = ButtonVariant.PRIMARY)
        },
        onCtrlEnter = { context.answer(draft.allow()) },
    ) {
        permissionSummary(proposal.toolName(), proposal.toolInput()).forEach { SummaryBlockView(it) }
        if (proposal.suggestions().isNotEmpty()) Remember(proposal, draft)
        AppTextField(draft.reason, Modifier.fillMaxWidth(), placeholder = "Tell Claude what to do instead (optional)", focusRequester = focus)
    }
}

@Composable
private fun Remember(proposal: PermissionProposal, draft: PermissionDraft) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label("Also remember", color = colors.textTertiary, style = Type.overline)
        proposal.suggestions().forEachIndexed { index, suggestion ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                AppCheckbox(draft.isRemembered(index), { draft.toggle(index) })
                Label(suggestion.description(), color = colors.textSecondary, maxLines = 2, tooltipWhenTruncated = true)
            }
        }
    }
}

@Composable
private fun SummaryBlockView(block: SummaryBlock) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        block.label?.let { Label(it, color = colors.textTertiary, style = Type.overline) }
        if (block.mono) CollapsibleMono(block.text) else Label(block.text, color = colors.textSecondary, maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun CollapsibleMono(text: String) {
    var expanded by remember(text) { mutableStateOf(false) }
    val lines = remember(text) { text.lines() }
    val shortened = remember(text) { collapsed(text) }
    MonoBlock(if (shortened != null && !expanded) shortened else text)
    if (shortened != null) GhostButton(if (expanded) "Show less" else "Show all (${lines.size} lines)", { expanded = !expanded })
}

/** The cut-down preview of a long text (by lines or by characters), or null when it fits whole. */
internal fun collapsed(text: String): String? {
    val lines = text.lines()
    if (lines.size <= COLLAPSED_LINES && text.length <= COLLAPSED_CHARS) return null
    return lines.take(COLLAPSED_LINES).joinToString("\n").take(COLLAPSED_CHARS) + "\n…"
}
