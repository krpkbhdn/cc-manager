package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.QuestionsAnswer
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Question

/** Keys the questions dialog understands; the composable maps raw key events onto these. */
enum class QuestionKey { UP, DOWN, LEFT, RIGHT, ENTER, SPACE, NEXT }

/** What the dialog has to do after a key: nothing, swallow it, send the answers, or move the keyboard into the free-answer field. */
enum class KeyEffect { IGNORED, HANDLED, SUBMIT, EDIT_OTHER }

/**
 * What the user has picked so far in a set of questions: chosen option indexes, an optional free answer, the step shown and
 * the keyboard cursor (an option index, or the option count for the "Other" row). All key behaviour lives here.
 */
class QuestionsDraft(val questions: List<Question>) {
    private val picked = questions.map { mutableStateListOf<Int>() }
    private val otherOn = mutableStateListOf(*Array(questions.size) { false })
    val other: List<TextFieldState> = questions.map { TextFieldState() }
    var step by mutableIntStateOf(0)
        private set
    var cursor by mutableIntStateOf(0)
        private set

    val current: Question get() = questions[step]
    val onLastStep: Boolean get() = step == questions.lastIndex
    val complete: Boolean get() = questions.indices.all(::answered)
    private val otherRow: Int get() = current.options().size

    /** Compact key help for the dialog footer. */
    val hints: String
        get() {
            val next = if (onLastStep) "submit" else "next"
            val tabs = if (questions.size > 1) " · ←→ tabs" else ""
            val pick = if (current.multiSelect()) "1-9 / Space / Enter toggle" else "1-9 pick · Enter pick & $next"
            return "↑↓ / Tab move · $pick · Ctrl+Enter $next$tabs"
        }

    fun goTo(target: Int) {
        step = target.coerceIn(0, questions.lastIndex)
        cursor = 0
    }

    fun isPicked(question: Int, option: Int) = option in picked[question]

    fun isOther(question: Int) = otherOn[question]

    /** The option whose preview is shown: the one under the cursor, else the last one picked, else the first one that has a preview. */
    fun previewOption(question: Int): Int? {
        val options = questions[question].options()
        val underCursor = cursor.takeIf { question == step && it < options.size && options[it].preview().isNotBlank() }
        return underCursor ?: picked[question].lastOrNull() ?: options.indexOfFirst { it.preview().isNotBlank() }.takeIf { it >= 0 }
    }

    fun toggle(question: Int, option: Int) {
        val chosen = picked[question]
        when {
            questions[question].multiSelect() -> if (option in chosen) chosen.remove(option) else chosen.add(option)
            else -> {
                chosen.clear()
                chosen.add(option)
                otherOn[question] = false
            }
        }
    }

    fun toggleOther(question: Int) {
        if (!questions[question].multiSelect()) picked[question].clear()
        otherOn[question] = !otherOn[question] || !questions[question].multiSelect()
    }

    fun pointAt(row: Int) {
        cursor = row.coerceIn(0, otherRow)
    }

    fun answered(question: Int) = values(question).isNotEmpty()

    fun answer() = QuestionsAnswer(questions.indices.associate { questions[it].text() to values(it) })

    fun press(key: QuestionKey): KeyEffect = when (key) {
        QuestionKey.UP -> handled { pointAt(cursor - 1) }
        QuestionKey.DOWN -> handled { pointAt(cursor + 1) }
        QuestionKey.LEFT -> handled { goTo(step - 1) }
        QuestionKey.RIGHT -> handled { goTo(step + 1) }
        QuestionKey.SPACE -> activateRow(advanceAfterSingle = false)
        QuestionKey.ENTER -> activateRow(advanceAfterSingle = true)
        QuestionKey.NEXT -> advance()
    }

    /** Digit [number] (1-based) picks that option and puts the cursor on it. */
    fun pressDigit(number: Int): KeyEffect {
        val option = number - 1
        if (option !in current.options().indices) return KeyEffect.IGNORED
        pointAt(option)
        toggle(step, option)
        return KeyEffect.HANDLED
    }

    /** The free-answer field gave up the keyboard upwards: back to the last option. */
    fun leaveOtherUpwards() = pointAt(otherRow - 1)

    /** Next question, or submit on the last one; nothing happens until the question shown is answered (all of them, to submit). */
    fun advance(): KeyEffect = when {
        !answered(step) -> KeyEffect.HANDLED
        !onLastStep -> handled { goTo(step + 1) }
        complete -> KeyEffect.SUBMIT
        else -> KeyEffect.HANDLED
    }

    private fun activateRow(advanceAfterSingle: Boolean): KeyEffect {
        if (cursor == otherRow) {
            toggleOther(step)
            return if (isOther(step)) KeyEffect.EDIT_OTHER else KeyEffect.HANDLED
        }
        toggle(step, cursor)
        return if (advanceAfterSingle && !current.multiSelect()) advance() else KeyEffect.HANDLED
    }

    private fun handled(action: () -> Unit): KeyEffect {
        action()
        return KeyEffect.HANDLED
    }

    private fun values(question: Int): List<String> {
        val labels = picked[question].sorted().map { questions[question].options()[it].label() }
        val free = other[question].text.toString().trim().takeIf { otherOn[question] && it.isNotEmpty() }
        return labels + listOfNotNull(free)
    }
}
