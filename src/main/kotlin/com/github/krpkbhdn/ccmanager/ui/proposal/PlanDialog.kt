package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import com.github.krpkbhdn.ccmanager.claude.proposal.PlanProposal
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.MarkdownView
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val HINTS = "Enter revises · Ctrl+Enter approves"

/** ExitPlanMode: the plan to read, a feedback field that sends Claude back to planning, and Approve with the chosen way to treat edits. Focus starts in the field, where Enter never approves. */
@Composable
internal fun PlanDialog(proposal: PlanProposal, title: FrameTitle, context: ProposalContext) {
    val draft = remember { context.draft { PlanDraft() } }
    val focus = remember { FocusRequester() }
    val revise = { draft.revise()?.let(context::answer) ?: Unit }
    ProposalDialogFrame(
        title, context, PLAN_WIDTH, focus, HINTS,
        leading = {
            Label("Edits", color = colors.textSecondary, style = Type.small)
            SegmentedControl(listOf(Segment(false, "Review"), Segment(true, "Auto-accept")), draft.autoAcceptEdits, { draft.autoAcceptEdits = it })
        },
        actions = {
            DialogButton("Revise", { revise() }, enabled = draft.canRevise)
            DialogButton("Approve", { context.answer(draft.approve()) }, variant = ButtonVariant.PRIMARY)
        },
        aboveFooter = {
            AppTextField(draft.feedback, Modifier.fillMaxWidth(), placeholder = "What should Claude change?", focusRequester = focus, onSubmit = { revise() })
        },
        onCtrlEnter = { context.answer(draft.approve()) },
    ) {
        if (proposal.planFilePath().isNotBlank()) Label(proposal.planFilePath(), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
        if (proposal.plan().isBlank()) Label("No plan text", color = colors.textTertiary) else MarkdownView(proposal.plan())
    }
}
