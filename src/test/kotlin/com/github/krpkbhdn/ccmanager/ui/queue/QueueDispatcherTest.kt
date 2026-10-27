package com.github.krpkbhdn.ccmanager.ui.queue

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class QueueDispatcherTest {

    private val dispatcher = QueueDispatcher()

    private fun observe(
        status: AgentStatus?,
        interrupted: Boolean = false,
        queueEmpty: Boolean = false,
        draft: Boolean = false,
        blocked: String? = null,
    ) = dispatcher.observe("s", QueueObservation(status, interrupted, queueEmpty, draft, blocked))

    private fun finishATurn() {
        observe(AgentStatus.IDLE)
        observe(AgentStatus.WORKING)
    }

    @Test
    fun sendsWhenATurnEndsAndThePromptIsQueued() {
        finishATurn()
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun sendsAtOnceWhenAPromptIsQueuedForAnIdleSession() {
        assertThat(observe(AgentStatus.IDLE, queueEmpty = true)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun waitsForTheDraftToClearWhenAPromptIsAddedWhileTyping() {
        observe(AgentStatus.IDLE, queueEmpty = true)
        assertThat(observe(AgentStatus.IDLE, draft = true)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun aPromptAddedWhileWorkingWaitsForTheTurnEnd() {
        observe(AgentStatus.WORKING, queueEmpty = true)
        assertThat(observe(AgentStatus.WORKING)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun aPromptAddedWhileTheSessionNeedsActionWaits() {
        observe(AgentStatus.IDLE, queueEmpty = true)
        assertThat(observe(AgentStatus.NEEDS_ACTION)).isEqualTo(QueueDecision.WAIT)
        assertThat(dispatcher.isWaiting("s")).isFalse()
    }

    @Test
    fun twoPromptsAddedWhileIdleSendOneNowAndTheNextAfterTheNextTurn() {
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
        dispatcher.sent("s")
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.WORKING)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun aPromptAddedWhileIdleAfterAnInterruptIsSent() {
        finishATurn()
        observe(AgentStatus.IDLE, interrupted = true, queueEmpty = true)
        dispatcher.promptAdded("s")
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun aPromptQueuedBeforeAnInterruptIsHeldUntilAnotherIsAdded() {
        finishATurn()
        observe(AgentStatus.IDLE, interrupted = true)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.WAIT)
        dispatcher.promptAdded("s")
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun doesNotSendWhileWorkingOrNeedingAction() {
        finishATurn()
        assertThat(observe(AgentStatus.WORKING)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.NEEDS_ACTION)).isEqualTo(QueueDecision.WAIT)
    }

    @Test
    fun anInterruptDoesNotSendAndDoesNotPauseTheNextTurn() {
        finishATurn()
        assertThat(observe(AgentStatus.IDLE, interrupted = true)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.WAIT)
        finishATurn()
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun waitsWhileAnUnsentDraftMayExistAndSendsOnceItIsGone() {
        finishATurn()
        assertThat(observe(AgentStatus.IDLE, draft = true)).isEqualTo(QueueDecision.WAIT)
        assertThat(dispatcher.isWaiting("s")).isTrue()
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun waitsWhileTheSessionIsBlocked() {
        finishATurn()
        assertThat(observe(AgentStatus.IDLE, blocked = "starting")).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
    }

    @Test
    fun anEmptyQueueSendsNothing() {
        finishATurn()
        assertThat(observe(AgentStatus.IDLE, queueEmpty = true)).isEqualTo(QueueDecision.WAIT)
        assertThat(dispatcher.isWaiting("s")).isFalse()
    }

    @Test
    fun sendsAgainUntilTheSendIsConfirmedSoOneTurnEndSendsOnePrompt() {
        finishATurn()
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.SEND)
        dispatcher.sent("s")
        assertThat(observe(AgentStatus.IDLE)).isEqualTo(QueueDecision.WAIT)
        assertThat(dispatcher.isWaiting("s")).isFalse()
    }

    @Test
    fun aSessionThatIsGoneOrOfflineIsForgottenAndNothingIsSent() {
        finishATurn()
        assertThat(observe(null)).isEqualTo(QueueDecision.WAIT)
        assertThat(observe(AgentStatus.OFFLINE)).isEqualTo(QueueDecision.WAIT)
        assertThat(dispatcher.isWaiting("s")).isFalse()
    }

    @Test
    fun retainSessionsForgetsEndedSessions() {
        finishATurn()
        dispatcher.retainSessions(emptySet())
        assertThat(dispatcher.isWaiting("s")).isFalse()
    }
}
