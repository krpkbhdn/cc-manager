package com.github.krpkbhdn.ccmanager.ui.teams

import com.github.krpkbhdn.ccmanager.ui.previewTeamService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

private const val WAIT_MILLIS = 5_000L

class TeamsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = mutableListOf<String>()
    private var confirmation: ConfirmRequest? = null
    private val service = previewTeamService()
    private val state = TeamsState(service, scope, { confirmation = it }, { errors += it })

    @BeforeEach
    fun start() {
        state.start()
        await { state.selected != null }
    }

    @AfterEach
    fun stop() {
        state.dispose()
        scope.cancel()
    }

    private fun roleNames() = service.find(state.selected!!.id).orElseThrow().roles.map { it.name() }

    @Test
    fun selectsTheFirstTeamAndFollowsSelection() {
        assertThat(state.selected?.name).isEqualTo("Dev team")

        state.select(state.teams.last())

        assertThat(state.selected?.name).isEqualTo("Docs squad")
    }

    @Test
    fun savingAnEditedRolePersistsItAndClosesTheDialog() {
        val team = state.selected!!
        state.editRole(team, team.roles[1])
        state.dialog!!.form.effort = "high"

        state.saveRole(state.dialog!!) { errors += it }
        await { service.find(team.id).orElseThrow().roles[1].selection().effort() == "high" && state.dialog == null }
    }

    @Test
    fun savingAnInvalidRoleKeepsTheDialogAndReportsTheMessage() {
        state.newRole(state.selected!!)
        val saved = mutableListOf<String>()

        state.saveRole(state.dialog!!) { saved += it }
        await { saved.isNotEmpty() }

        assertThat(saved.single()).contains("Describe when")
        assertThat(state.dialog).isNotNull()
    }

    @Test
    fun aNewRoleGetsAUniqueNameAndTakingTheLeadDemotesTheOldLead() {
        val team = state.selected!!
        state.newRole(team)
        val form = state.dialog!!.form
        assertThat(form.name.text.toString()).isEqualTo("agent")
        form.description.edit { replace(0, length, "Does things") }
        form.prompt.edit { replace(0, length, "You do things") }
        form.lead = true

        state.saveRole(state.dialog!!) { errors += it }
        await { service.find(team.id).orElseThrow().roles.size == 4 }

        val roles = service.find(team.id).orElseThrow().roles
        assertThat(roles.filter { it.lead() }.map { it.name() }).containsExactly("agent")
    }

    @Test
    fun movingAndDuplicatingChangeTheRoster() {
        val team = state.selected!!

        state.moveRole(team, team.roles[2], -1)
        await { roleNames() == listOf("architect", "tester", "developer") && state.selected!!.roles.map { it.name() } == roleNames() }
        state.duplicateRole(state.selected!!, state.selected!!.roles[1])
        await { roleNames().size == 4 }

        assertThat(roleNames()).contains("tester-copy")
    }

    @Test
    fun theLeadCannotBeRemovedAndOtherRolesAskFirst() {
        val team = state.selected!!

        state.requestDeleteRole(team, team.roles[0])
        assertThat(errors.single()).contains("another agent the lead")
        assertThat(confirmation).isNull()

        state.requestDeleteRole(team, team.roles[2])
        confirmation!!.onConfirm()
        await { roleNames() == listOf("architect", "developer") }
    }

    @Test
    fun closingADirtyDialogAsksBeforeDiscardingAndACleanOneClosesAtOnce() {
        val team = state.selected!!
        state.editRole(team, team.roles[1])
        state.requestCloseDialog()
        assertThat(state.dialog).isNull()

        state.editRole(team, team.roles[1])
        state.dialog!!.form.effort = "max"
        state.requestCloseDialog()
        assertThat(state.dialog).isNotNull()
        confirmation!!.onConfirm()
        assertThat(state.dialog).isNull()
    }

    @Test
    fun deletingATeamAsksFirstAndSelectionMovesOn() {
        val team = state.selected!!

        state.requestDeleteTeam(team)
        assertThat(service.list()).hasSize(2)
        confirmation!!.onConfirm()
        await { service.list().size == 1 && state.selected?.id != team.id }

        assertThat(state.selected?.name).isEqualTo("Docs squad")
    }

    @Test
    fun newTeamIsCreatedWithAStarterLeadAndSelected() {
        state.newTeam()
        await { state.selected?.name == "New team" }

        assertThat(state.selected!!.roles.map { it.name() }).containsExactly("lead")
    }

    @Test
    fun projectDefaultTogglesAndFallsBackToTheFirstTeam() {
        assertThat(state.defaultTeamOf(42)?.name).isEqualTo("Dev team")
    }

    private fun await(condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + WAIT_MILLIS
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(20)
        assertThat(condition()).describedAs("condition within ${WAIT_MILLIS}ms").isTrue()
    }
}
