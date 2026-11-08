package com.github.krpkbhdn.ccmanager.ui.proposal

import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
import com.github.krpkbhdn.ccmanager.ui.await
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import tools.jackson.databind.json.JsonMapper

class ProposalStateTest {
    private val proposal = PermissionProposal("p", "s", Instant.now(), "", "Bash", JsonMapper.builder().build().readTree("{}"), emptyList())
    private val service = mock(ProposalService::class.java).also { `when`(it.pending()).thenReturn(listOf(proposal)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = mutableListOf<String>()
    private val state = ProposalState(service, scope, { errors += it }).also {
        it.start()
        await { it.of("s").isNotEmpty() }
    }

    @AfterEach
    fun close() = scope.cancel()

    @Test
    fun aTakenAnswerHidesTheDialog() {
        state.answer(proposal, PermissionAllow.once())

        await { state.dialogFor("s") == null }
    }

    @Test
    fun aFailedAnswerKeepsTheDialogForARetryAndReportsTheError() {
        `when`(service.answer(anyString(), any())).thenThrow(IllegalStateException("backend down"))

        state.answer(proposal, PermissionAllow.once())

        await { errors.isNotEmpty() }
        assertThat(state.dialogFor("s")).isSameAs(proposal)
    }

    @Test
    fun anAnswerTheBackendNoLongerAcceptsIsReported() {
        `when`(service.answer(anyString(), any())).thenReturn(false)

        state.answer(proposal, PermissionAllow.once())

        await { errors.isNotEmpty() }
        assertThat(errors.single()).contains("already answered or expired")
    }

    @Test
    fun aProposalIsAnnouncedOnceWhenItArrives() {
        val arrived = java.util.concurrent.CopyOnWriteArrayList<String>()
        val announcing = ProposalState(service, scope, { errors += it }, onArrived = { arrived += it.id() })

        val next = PermissionProposal("q", "s", Instant.now(), "", "Bash", JsonMapper.builder().build().readTree("{}"), emptyList())

        announcing.start()
        await { arrived.isNotEmpty() }
        `when`(service.pending()).thenReturn(listOf(proposal, next))
        announcing.start()
        await { arrived.size == 2 }

        assertThat(arrived).containsExactly("p", "q")
    }
}
