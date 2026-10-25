package com.github.krpkbhdn.ccmanager.ui.proposal

import androidx.compose.ui.input.key.Key
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PlanProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanApproval
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PlanRejection
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.QuestionsAnswer
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Option
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal.Question
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import tools.jackson.databind.json.JsonMapper

/** Real key events through a real Compose scene: focus traversal, the question keys and the default actions of the other dialogs. */
class ProposalDialogKeysTest {
    private val mapper = JsonMapper.builder().build()
    private val colors = Question("Pick a color", "Color", listOf(Option("Red", "", ""), Option("Blue", "", ""), Option("Green", "", "")), false)
    private val fruits = Question("Pick fruits", "Fruits", listOf(Option("Apple", "", ""), Option("Pear", "", "")), true)
    private var harness: ProposalDialogHarness? = null

    @AfterEach
    fun close() {
        harness?.close()
    }

    private fun questions(vararg asked: Question): Pair<ProposalDialogHarness, QuestionsDraft> {
        val proposal = QuestionsProposal("q", "s", Instant.now(), "", mapper.createObjectNode(), asked.toList())
        val scene = ProposalDialogHarness(proposal) { context, title -> QuestionsDialog(proposal, title, context) }
        harness = scene
        return scene to scene.state.draft(proposal) { QuestionsDraft(proposal.questions()) }
    }

    @Test
    fun tabStepsOptionByOptionThroughTheOtherRowAndShiftTabGoesBack() {
        val (scene, draft) = questions(colors)
        assertThat(draft.cursor).isEqualTo(0)

        repeat(3) { scene.press(Key.Tab) }
        assertThat(draft.cursor).isEqualTo(3)
        scene.press(Key.Tab, shift = true)
        assertThat(draft.cursor).isEqualTo(2)
        scene.press(Key.Tab, shift = true)
        assertThat(draft.cursor).isEqualTo(1)
    }

    @Test
    fun tabAfterTheOtherRowReachesTheFooterButton() {
        val (scene, draft) = questions(colors)
        draft.toggle(0, 2)

        repeat(4) { scene.press(Key.Tab) }
        scene.press(Key.Enter)

        assertThat(scene.answered()).isEqualTo(QuestionsAnswer(mapOf("Pick a color" to listOf("Green"))))
    }

    @Test
    fun downAndUpMoveTheFocusedRow() {
        val (scene, draft) = questions(colors)

        scene.press(Key.DirectionDown)
        scene.press(Key.DirectionDown)
        assertThat(draft.cursor).isEqualTo(2)
        scene.press(Key.DirectionUp)
        assertThat(draft.cursor).isEqualTo(1)
    }

    @Test
    fun enterOnASingleChoiceAdvancesAndOnTheLastTabSubmits() {
        val (scene, draft) = questions(colors, fruits.copyAsSingle())

        scene.press(Key.DirectionDown)
        scene.press(Key.Enter)
        assertThat(draft.step).isEqualTo(1)
        scene.press(Key.Enter)

        assertThat(scene.answered()).isEqualTo(QuestionsAnswer(mapOf("Pick a color" to listOf("Blue"), "Pick fruits" to listOf("Apple"))))
    }

    @Test
    fun enterAndSpaceToggleInAMultiSelectAndCtrlEnterSubmits() {
        val (scene, draft) = questions(fruits)

        scene.press(Key.Enter)
        scene.press(Key.DirectionDown)
        scene.press(Key.Spacebar)
        assertThat(draft.step).isEqualTo(0)
        assertThat(draft.answer().answers()["Pick fruits"]).containsExactly("Apple", "Pear")
        scene.press(Key.Enter, ctrl = true)

        assertThat(scene.answered()).isEqualTo(QuestionsAnswer(mapOf("Pick fruits" to listOf("Apple", "Pear"))))
    }

    @Test
    fun ctrlEnterAdvancesToTheNextQuestion() {
        val (scene, draft) = questions(fruits, colors)

        scene.press(Key.Enter, ctrl = true)
        assertThat(draft.step).isEqualTo(0)
        scene.press(Key.Spacebar)
        scene.press(Key.Enter, ctrl = true)

        assertThat(draft.step).isEqualTo(1)
    }

    @Test
    fun digitsPickTheNumberedOption() {
        val (scene, draft) = questions(colors)

        scene.press(Key.Three)

        assertThat(draft.answer().answers()["Pick a color"]).containsExactly("Green")
    }

    @Test
    fun inTheOtherFieldDigitsAndArrowsStayInTheFieldAndEnterSubmits() {
        val (scene, draft) = questions(colors)
        repeat(3) { scene.press(Key.DirectionDown) }

        scene.press(Key.Enter)
        draft.other[0].edit { replace(0, length, "a12") }
        scene.press(Key.Three)
        scene.press(Key.DirectionDown)
        scene.press(Key.DirectionLeft)
        scene.press(Key.DirectionRight)

        assertThat(draft.other[0].text.toString()).isEqualTo("a12")
        assertThat(draft.answer().answers()["Pick a color"]).containsExactly("a12")
        scene.press(Key.Enter)
        assertThat(scene.answered()).isEqualTo(QuestionsAnswer(mapOf("Pick a color" to listOf("a12"))))
    }

    @Test
    fun plainEnterInThePlanFeedbackNeverApproves() {
        val proposal = PlanProposal("p", "s", Instant.now(), "", mapper.createObjectNode(), "# Plan", "")
        val scene = ProposalDialogHarness(proposal) { context, title -> PlanDialog(proposal, title, context) }.also { harness = it }
        val state = scene.state

        scene.press(Key.Enter)
        scene.assertNoAnswer()
        state.draft(proposal) { PlanDraft() }.feedback.edit { replace(0, length, "go") }
        scene.settle()
        scene.press(Key.Enter)

        assertThat(scene.answered()).isEqualTo(PlanRejection("go"))
    }

    @Test
    fun ctrlEnterApprovesThePlanForReview() {
        val proposal = PlanProposal("p", "s", Instant.now(), "", mapper.createObjectNode(), "# Plan", "")
        val scene = ProposalDialogHarness(proposal) { context, title -> PlanDialog(proposal, title, context) }.also { harness = it }
        val state = scene.state

        scene.press(Key.Enter, ctrl = true)

        assertThat(scene.answered()).isEqualTo(PlanApproval(false))
    }

    @Test
    fun permissionStartsOnAllowOnceSoEnterAllowsOnce() {
        val proposal = PermissionProposal("p", "s", Instant.now(), "", "Bash", mapper.readTree("""{"command":"ls"}"""), emptyList())
        val scene = ProposalDialogHarness(proposal) { context, title -> PermissionDialog(proposal, title, context) }.also { harness = it }

        scene.press(Key.Enter)

        assertThat(scene.answered()).isEqualTo(PermissionAllow.once())
    }

    private fun Question.copyAsSingle() = Question(text(), header(), options(), false)
}
