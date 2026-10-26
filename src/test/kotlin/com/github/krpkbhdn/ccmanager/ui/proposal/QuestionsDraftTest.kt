package com.github.krpkbhdn.ccmanager.ui.proposal

import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Option
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Question
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QuestionsDraftTest {
    private val single = Question("Pick one", "One", listOf(Option("A", "", "code A"), Option("B", "", "")), false)
    private val multi = Question("Pick many", "Many", listOf(Option("X", "", ""), Option("Y", "", ""), Option("Z", "", "")), true)
    private val draft = QuestionsDraft(listOf(single, multi))

    @Test
    fun submitNeedsEveryQuestionAnswered() {
        assertThat(draft.complete).isFalse()
        draft.toggle(0, 1)
        assertThat(draft.complete).isFalse()
        draft.toggle(1, 2)

        assertThat(draft.complete).isTrue()
    }

    @Test
    fun singleChoiceReplacesThePreviousPick() {
        draft.toggle(0, 0)
        draft.toggle(0, 1)

        assertThat(draft.answer().answers()["Pick one"]).containsExactly("B")
    }

    @Test
    fun multiSelectKeepsPicksInOptionOrderAndAddsTheFreeAnswer() {
        draft.toggle(1, 2)
        draft.toggle(1, 0)
        draft.toggleOther(1)
        draft.other[1].edit { replace(0, length, " W ") }

        assertThat(draft.answer().answers()["Pick many"]).containsExactly("X", "Z", "W")
    }

    @Test
    fun blankFreeAnswerDoesNotCount() {
        draft.toggleOther(0)

        assertThat(draft.answered(0)).isFalse()
        draft.other[0].edit { replace(0, length, "mine") }
        assertThat(draft.answer().answers()["Pick one"]).containsExactly("mine")
    }

    @Test
    fun pickingAnOptionDropsTheFreeAnswerOfASingleChoice() {
        draft.toggleOther(0)
        draft.other[0].edit { replace(0, length, "mine") }
        draft.toggle(0, 0)

        assertThat(draft.answer().answers()["Pick one"]).containsExactly("A")
        assertThat(draft.isOther(0)).isFalse()
    }

    @Test
    fun previewFollowsCursorThenPickThenFirstOptionWithOne() {
        assertThat(draft.previewOption(0)).isEqualTo(0)
        draft.press(QuestionKey.DOWN)
        draft.toggle(0, 1)
        assertThat(draft.previewOption(0)).isEqualTo(1)
        assertThat(draft.previewOption(1)).isNull()
    }

    @Test
    fun arrowsMoveTheCursorOverOptionsAndTheOtherRowWithoutWrapping() {
        draft.press(QuestionKey.UP)
        assertThat(draft.cursor).isEqualTo(0)
        repeat(5) { draft.press(QuestionKey.DOWN) }

        assertThat(draft.cursor).isEqualTo(2)
    }

    @Test
    fun enterOnASingleChoiceSelectsAndGoesToTheNextQuestion() {
        draft.press(QuestionKey.DOWN)

        assertThat(draft.press(QuestionKey.ENTER)).isEqualTo(KeyEffect.HANDLED)
        assertThat(draft.answer().answers()["Pick one"]).containsExactly("B")
        assertThat(draft.step).isEqualTo(1)
        assertThat(draft.cursor).isEqualTo(0)
    }

    @Test
    fun enterOnTheLastSingleChoiceSubmitsOnlyWhenEverythingIsAnswered() {
        val last = QuestionsDraft(listOf(single))

        assertThat(last.press(QuestionKey.ENTER)).isEqualTo(KeyEffect.SUBMIT)
        val two = QuestionsDraft(listOf(single, single))
        two.goTo(1)
        assertThat(two.press(QuestionKey.ENTER)).isEqualTo(KeyEffect.HANDLED)
    }

    @Test
    fun enterAndSpaceToggleInAMultiSelectAndNeverAdvance() {
        draft.goTo(1)
        draft.press(QuestionKey.ENTER)
        draft.press(QuestionKey.DOWN)
        draft.press(QuestionKey.SPACE)
        draft.press(QuestionKey.SPACE)

        assertThat(draft.step).isEqualTo(1)
        assertThat(draft.answer().answers()["Pick many"]).containsExactly("X")
    }

    @Test
    fun ctrlEnterAdvancesOnlyAnAnsweredQuestionAndSubmitsOnTheLast() {
        assertThat(draft.press(QuestionKey.NEXT)).isEqualTo(KeyEffect.HANDLED)
        assertThat(draft.step).isEqualTo(0)
        draft.toggle(0, 0)
        draft.press(QuestionKey.NEXT)
        assertThat(draft.step).isEqualTo(1)
        draft.toggle(1, 1)

        assertThat(draft.press(QuestionKey.NEXT)).isEqualTo(KeyEffect.SUBMIT)
    }

    @Test
    fun enterOnTheOtherRowOpensTheFieldAndASecondSpaceOnAMultiClosesIt() {
        repeat(2) { draft.press(QuestionKey.DOWN) }

        assertThat(draft.press(QuestionKey.ENTER)).isEqualTo(KeyEffect.EDIT_OTHER)
        assertThat(draft.isOther(0)).isTrue()
        draft.goTo(1)
        repeat(3) { draft.press(QuestionKey.DOWN) }
        assertThat(draft.press(QuestionKey.SPACE)).isEqualTo(KeyEffect.EDIT_OTHER)
        assertThat(draft.press(QuestionKey.SPACE)).isEqualTo(KeyEffect.HANDLED)
        assertThat(draft.isOther(1)).isFalse()
    }

    @Test
    fun digitsPickTheNumberedOptionAndIgnoreMissingOnes() {
        assertThat(draft.pressDigit(2)).isEqualTo(KeyEffect.HANDLED)
        assertThat(draft.answer().answers()["Pick one"]).containsExactly("B")
        assertThat(draft.cursor).isEqualTo(1)
        assertThat(draft.pressDigit(9)).isEqualTo(KeyEffect.IGNORED)
    }

    @Test
    fun leftAndRightSwitchTabsWithinBounds() {
        draft.press(QuestionKey.LEFT)
        assertThat(draft.step).isEqualTo(0)
        draft.press(QuestionKey.RIGHT)
        draft.press(QuestionKey.RIGHT)

        assertThat(draft.step).isEqualTo(1)
    }

    @Test
    fun leavingTheOtherFieldUpwardsPutsTheCursorOnTheLastOption() {
        repeat(2) { draft.press(QuestionKey.DOWN) }
        draft.leaveOtherUpwards()

        assertThat(draft.cursor).isEqualTo(1)
    }

    @Test
    fun hintsNameTheActionOfTheStep() {
        assertThat(draft.hints).contains("Enter pick & next").contains("←→ tabs")
        draft.goTo(1)
        assertThat(draft.hints).contains("toggle").contains("Ctrl+Enter submit")
    }
}
