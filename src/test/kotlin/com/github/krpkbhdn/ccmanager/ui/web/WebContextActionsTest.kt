package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.ui.geometry.Offset
import com.github.krpkbhdn.ccmanager.ui.web.browser.FakePageHost
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WebContextActionsTest {
    private val host = FakePageHost()
    private val view = WebView(1, host)
    private val scope = TestScope()
    private val notices = mutableListOf<String>()
    private val actions = WebContextActions(scope, notify = { notices += it })
    private val layer = LiteTarget("div.card", "div", "", listOf("card"), null)

    private fun stack(x: Double = 10.0, y: Double = 20.0) = InspectorMessage.Stack(x, y, listOf(layer))

    private fun inspect(reason: String) = InspectorMessage.Inspect(reason, ElementTarget("div.card", "", "div", "", emptyList(), "", "", null, null, emptyMap(), null, null, null, null))

    @Test
    fun openAsksThePageForTheStackAndAnswerFillsTheMenu() {
        actions.open(view, 10.0, 20.0, Offset(5f, 6f))
        assertThat(host.scripts).contains(InspectorBridge.stackAt(10.0, 20.0))
        actions.onStack(view, stack())
        assertThat(view.contextMenu!!.items).containsExactly(layer)
    }

    @Test
    fun aStackForAnotherPointIsIgnored() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.onStack(view, stack(x = 1.0))
        assertThat(view.contextMenu!!.items).isEmpty()
    }

    @Test
    fun feedbackInspectStartsTheDraftAndSwitchesMenuToPopover() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.requestInspect(view, "div.card", "feedback")
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked?.selector).isEqualTo("div.card")
        assertThat(view.contextMenu!!.feedback).isTrue()
    }

    @Test
    fun stylesInspectStartsTheDraftAndClosesTheMenu() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.requestInspect(view, "div.card", "styles")
        actions.onInspect(view, inspect("styles"))
        assertThat(view.picked).isNotNull()
        assertThat(view.contextMenu).isNull()
    }

    @Test
    fun anInspectNobodyAskedForIsDropped() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked).isNull()
        assertThat(view.contextMenu!!.feedback).isFalse()
    }

    @Test
    fun anInspectForAnotherSelectorOrReasonIsDroppedAndTheRequestIsSpent() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.requestInspect(view, "other", "feedback")
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked).isNull()
        actions.requestInspect(view, "div.card", "styles")
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked).isNull()
        actions.onInspect(view, inspect("styles"))
        assertThat(view.picked).isNull()
    }

    @Test
    fun anOpenDraftKeepsTheMenuWithANoticeAndAsksNothing() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        view.beginDraft(inspect("x").target)
        view.comment.setTextAndPlaceCursorAtEnd("typed")
        host.scripts.clear()
        actions.requestInspect(view, "div.card", "feedback")
        assertThat(view.contextMenu!!.notice).isEqualTo(DRAFT_FIRST_NOTICE)
        assertThat(host.scripts).isEmpty()
    }

    @Test
    fun resetClosesTheMenuAndDropsThePendingRequest() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.requestInspect(view, "div.card", "feedback")
        actions.reset(view)
        assertThat(view.contextMenu).isNull()
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked).isNull()
    }

    @Test
    fun anInspectNobodyAnswersExpiresWithTheGoneNoticeAndALateAnswerIsDropped() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.requestInspect(view, "div.card", "feedback")
        scope.advanceTimeBy(INSPECT_TIMEOUT_MILLIS + 1)
        scope.runCurrent()
        assertThat(view.contextMenu!!.notice).isEqualTo(ELEMENT_GONE_NOTICE)
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked).isNull()
    }

    @Test
    fun anAnsweredInspectDoesNotExpireLater() {
        actions.open(view, 10.0, 20.0, Offset.Zero)
        actions.requestInspect(view, "div.card", "feedback")
        actions.onInspect(view, inspect("feedback"))
        scope.advanceTimeBy(INSPECT_TIMEOUT_MILLIS + 1)
        scope.runCurrent()
        assertThat(view.contextMenu!!.notice).isNull()
    }

    @Test
    fun quickFeedbackInspectsTheTopLayerAndOpensThePopover() {
        view.lastPointer = PointerSpot(Offset(10f, 20f), Offset(5f, 6f))
        actions.openQuick(view)
        assertThat(host.scripts).contains(InspectorBridge.stackAt(10.0, 20.0))
        actions.onStack(view, stack())
        assertThat(host.scripts).contains(InspectorBridge.describeSelector("div.card", REASON_FEEDBACK))
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked?.selector).isEqualTo("div.card")
        assertThat(view.contextMenu!!.feedback).isTrue()
        assertThat(view.contextMenu!!.canvasOffset).isEqualTo(Offset(5f, 6f))
    }

    @Test
    fun quickFeedbackOnAnEmptyStackDismissesWithANotice() {
        view.lastPointer = PointerSpot(Offset(10f, 20f), Offset.Zero)
        actions.openQuick(view)
        actions.onStack(view, InspectorMessage.Stack(10.0, 20.0, emptyList()))
        assertThat(view.contextMenu).isNull()
        assertThat(notices).containsExactly(NOTHING_HERE_NOTICE)
    }

    @Test
    fun quickFeedbackWithoutAPointerOnlyNotifies() {
        actions.openQuick(view)
        assertThat(view.contextMenu).isNull()
        assertThat(notices).containsExactly(NO_POINTER_NOTICE)
        assertThat(host.scripts).isEmpty()
    }

    @Test
    fun quickFeedbackStillIgnoresAnInspectWithoutAPendingRequest() {
        view.lastPointer = PointerSpot(Offset(10f, 20f), Offset.Zero)
        actions.openQuick(view)
        actions.onInspect(view, inspect("feedback"))
        assertThat(view.picked).isNull()
    }

    @Test
    fun theSimilarOutlineIsClearedOnOpenDismissAndReset() {
        val clear = InspectorBridge.showSimilar("")
        actions.open(view, 10.0, 20.0, Offset.Zero)
        assertThat(host.scripts).contains(clear)
        host.scripts.clear()
        WebContextActions.dismiss(view)
        assertThat(host.scripts).contains(clear)
        host.scripts.clear()
        actions.reset(view)
        assertThat(host.scripts).contains(clear)
    }
}
