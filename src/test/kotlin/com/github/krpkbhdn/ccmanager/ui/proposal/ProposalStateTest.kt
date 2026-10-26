package com.github.krpkbhdn.ccmanager.ui.proposal

import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalAnswer.PermissionAllow
import com.github.krpkbhdn.ccmanager.claude.proposal.ProposalService
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

private const val WAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 20L

class ProposalStateTest {
    private val proposal = PermissionProposal("p", "s", Instant.now(), "", "Bash", JsonMapper.builder().build().readTree("{}"), emptyList())
    private val service = mock(ProposalService::class.java).also { `when`(it.pending()).thenReturn(listOf(proposal)) }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = mutableListOf<String>()
    private val state = ProposalState(service, scope, { errors += it }).also {
        it.start()
        until { it.of("s").isNotEmpty() }
    }

    @AfterEach
    fun close() = scope.cancel()

    private fun until(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
        assertThat(condition()).isTrue()
    }

    @Test
    fun aTakenAnswerHidesTheDialog() {
        state.answer(proposal, PermissionAllow.once())

        until { state.dialogFor("s") == null }
    }

    @Test
    fun aFailedAnswerKeepsTheDialogForARetryAndReportsTheError() {
        `when`(service.answer(anyString(), any())).thenThrow(IllegalStateException("backend down"))

        state.answer(proposal, PermissionAllow.once())

        until { errors.isNotEmpty() }
        assertThat(state.dialogFor("s")).isSameAs(proposal)
    }
}
