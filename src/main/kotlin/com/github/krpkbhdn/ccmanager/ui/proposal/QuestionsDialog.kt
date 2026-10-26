package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Kbd
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.RadioMark
import com.github.krpkbhdn.ccmanager.ui.components.Segment
import com.github.krpkbhdn.ccmanager.ui.components.SegmentedControl
import com.github.krpkbhdn.ccmanager.ui.extensions.MonoBlock
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val FOCUS_BORDER = 1.dp
/** Lines the check box and radio mark up with the first text line of their row. */
private val CHECKBOX_NUDGE = 1.dp
private val RADIO_NUDGE = 2.dp
private val DIGIT_KEYS = listOf(Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.Six, Key.Seven, Key.Eight, Key.Nine)
private val NAVIGATION_KEYS = mapOf(
    Key.DirectionUp to QuestionKey.UP, Key.DirectionDown to QuestionKey.DOWN, Key.DirectionLeft to QuestionKey.LEFT,
    Key.DirectionRight to QuestionKey.RIGHT, Key.Enter to QuestionKey.ENTER, Key.NumPadEnter to QuestionKey.ENTER, Key.Spacebar to QuestionKey.SPACE,
)

/** AskUserQuestion: one question per tab; the keyboard map lives in [QuestionsDraft], this only translates key events. */
@Composable
internal fun QuestionsDialog(proposal: QuestionsProposal, title: FrameTitle, context: ProposalContext) {
    val draft = remember { context.draft { QuestionsDraft(proposal.questions()) } }
    val rowFocus = remember(draft.step) { List(draft.current.options().size + 1) { FocusRequester() } }
    val hasPreview = proposal.questions().any { question -> question.options().any { it.preview().isNotBlank() } }
    val perform = { effect: KeyEffect ->
        if (effect == KeyEffect.SUBMIT) context.answer(draft.answer())
        effect != KeyEffect.IGNORED
    }
    ProposalDialogFrame(
        title, context, if (hasPreview) QUESTIONS_PREVIEW_WIDTH else QUESTIONS_WIDTH, rowFocus.first(), draft.hints,
        actions = {
            if (draft.step > 0) DialogButton("Back", { draft.goTo(draft.step - 1) }, variant = ButtonVariant.GHOST)
            if (draft.onLastStep) {
                DialogButton("Submit", { perform(draft.advance()) }, variant = ButtonVariant.PRIMARY, enabled = draft.complete)
            } else {
                DialogButton("Next", { perform(draft.advance()) }, enabled = draft.answered(draft.step))
            }
        },
        onCtrlEnter = { perform(draft.press(QuestionKey.NEXT)) },
    ) {
        if (draft.questions.size > 1) {
            SegmentedControl(draft.questions.mapIndexed { index, q -> Segment(index, q.header().ifBlank { "Question ${index + 1}" }) }, draft.step, { draft.goTo(it) })
        }
        QuestionStep(draft, rowFocus, perform)
    }
}

private fun questionKeyOf(event: KeyEvent): QuestionKey? =
    NAVIGATION_KEYS[event.key].takeIf { event.type == KeyEventType.KeyDown && !event.isCtrlPressed && !event.isAltPressed && !event.isMetaPressed }

@Composable
private fun QuestionStep(draft: QuestionsDraft, rowFocus: List<FocusRequester>, perform: (KeyEffect) -> Boolean) {
    val index = draft.step
    val question = draft.current
    val otherRow = question.options().size
    LaunchedEffect(index, draft.cursor) { runCatching { rowFocus[draft.cursor].requestFocus() } }
    val preview = draft.previewOption(index)?.let { question.options()[it].preview() }.orEmpty()
    Label(question.text(), style = Type.subtitle, maxLines = Int.MAX_VALUE)
    if (question.multiSelect()) Label("Select all that apply", color = colors.textTertiary, style = Type.small)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.lg)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Column(
                Modifier.onPreviewKeyEvent { event ->
                    val digit = DIGIT_KEYS.indexOf(event.key).takeIf { event.type == KeyEventType.KeyDown && it >= 0 }
                    if (digit != null) perform(draft.pressDigit(digit + 1)) else questionKeyOf(event)?.let { perform(draft.press(it)) } ?: false
                },
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                question.options().forEachIndexed { option, value ->
                    OptionRow(value.label(), value.description(), draft.isPicked(index, option), question.multiSelect(), option + 1, rowFocus[option], { draft.pointAt(option) }) {
                        draft.pointAt(option)
                        draft.toggle(index, option)
                    }
                }
                OptionRow("Other…", "", draft.isOther(index), question.multiSelect(), null, rowFocus[otherRow], { draft.pointAt(otherRow) }) {
                    draft.pointAt(otherRow)
                    perform(draft.press(QuestionKey.SPACE))
                }
            }
            if (draft.isOther(index)) OtherField(draft, rowFocus[otherRow - 1], perform)
        }
        if (preview.isNotBlank()) Box(Modifier.weight(1f)) { MonoBlock(preview) }
    }
}

/** A focus stop of its own: Tab, Shift+Tab and the arrows all land on rows, and [onFocused] keeps the draft's cursor on the focused one. */
@Composable
private fun OptionRow(
    label: String,
    description: String,
    selected: Boolean,
    multi: Boolean,
    number: Int?,
    focus: FocusRequester,
    onFocused: () -> Unit,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(Radius.md)
    var focused by remember { mutableStateOf(false) }
    Pressable(
        onClick,
        Modifier.fillMaxWidth()
            .focusRequester(focus)
            .onFocusChanged { focused = it.isFocused; if (it.isFocused) onFocused() }
            .then(if (focused) Modifier.border(FOCUS_BORDER, colors.accent, shape) else Modifier),
        shape,
        base = if (selected) colors.accentSoft else Color.Transparent,
    ) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            val mark = Modifier.focusProperties { canFocus = false }
            if (multi) AppCheckbox(selected, { onClick() }, mark.padding(top = CHECKBOX_NUDGE)) else RadioMark(selected, Modifier.padding(top = RADIO_NUDGE))
            Column(Modifier.weight(1f)) {
                Label(label, style = Type.bodyMedium, maxLines = Int.MAX_VALUE)
                if (description.isNotBlank()) Label(description, color = colors.textSecondary, style = Type.small, maxLines = Int.MAX_VALUE)
            }
            if (number != null && number <= DIGIT_KEYS.size) Kbd(number.toString())
        }
    }
}

@Composable
private fun OtherField(draft: QuestionsDraft, lastOption: FocusRequester, perform: (KeyEffect) -> Boolean) {
    val index = draft.step
    val focus = remember(index) { FocusRequester() }
    LaunchedEffect(index) { runCatching { focus.requestFocus() } }
    AppTextField(
        draft.other[index],
        Modifier.fillMaxWidth().padding(start = Spacing.xl + Spacing.xs).onPreviewKeyEvent { event ->
            (event.type == KeyEventType.KeyDown && event.key == Key.DirectionUp).also { up ->
                if (up) { draft.leaveOtherUpwards(); runCatching { lastOption.requestFocus() } }
            }
        },
        placeholder = "Your own answer",
        focusRequester = focus,
        onSubmit = { perform(draft.advance()) },
    )
}
