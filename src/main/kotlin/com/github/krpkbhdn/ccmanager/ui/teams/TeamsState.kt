package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.team.RoleDraft
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.team.TeamService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NEW_TEAM_NAME = "New team"
private const val NEW_ROLE_NAME = "agent"
private const val COPY_SUFFIX = "-copy"
private val starterLead = RoleDraft(
    "lead", "Leads the task: splits it into steps and delegates them to the other agents.",
    "You lead the team. Break the task into small steps, delegate them to your agents and check what they report.", null, null, null, true,
)

/** The role being created ([index] null) or edited in the dialog. */
class RoleDialogState(val teamId: Long, val index: Int?, val form: RoleForm)

/**
 * Teams the Teams screen lists and the roster of the selected one, plus each project's default team. Every role change
 * is saved to the team at once; writes run off the UI thread and failures go to [report].
 */
class TeamsState(
    private val service: TeamService,
    private val scope: CoroutineScope,
    private val confirm: (ConfirmRequest) -> Unit,
    private val report: (String) -> Unit,
) {
    var teams by mutableStateOf<List<Team>>(emptyList())
        private set
    var projectDefaults by mutableStateOf<Map<Long, Long>>(emptyMap())
        private set
    var dialog by mutableStateOf<RoleDialogState?>(null)
        private set

    private var selectedId by mutableStateOf<Long?>(null)
    private var unsubscribe: Runnable? = null

    val selected: Team? get() = teams.firstOrNull { it.id == selectedId } ?: teams.firstOrNull()

    fun start() {
        unsubscribe = service.subscribe { scope.launch { reload() } }
        scope.launch { reload() }
    }

    fun dispose() = unsubscribe?.run()

    fun find(id: Long): Team? = teams.firstOrNull { it.id == id }

    fun defaultTeamOf(projectId: Long): Team? = projectDefaults[projectId]?.let(::find) ?: teams.firstOrNull()

    fun select(team: Team) {
        selectedId = team.id
    }

    fun newTeam() = run { selectedId = withContext(Dispatchers.IO) { service.create(NEW_TEAM_NAME, listOf(starterLead)) }.id }

    fun renameTeam(team: Team, name: String) = run { withContext(Dispatchers.IO) { service.update(team.id, name, team.drafts()) } }

    fun duplicateTeam(team: Team) = run { selectedId = withContext(Dispatchers.IO) { service.duplicate(team.id) }.id }

    fun requestDeleteTeam(team: Team) {
        confirm(ConfirmRequest("Delete team", "Delete the team \"${team.name}\" and its agents? Running sessions keep their agents.", "Delete") {
            run { withContext(Dispatchers.IO) { service.delete(team.id) } }
        })
    }

    fun setProjectDefault(projectId: Long, teamId: Long?) = run { withContext(Dispatchers.IO) { service.setProjectDefault(projectId, teamId) } }

    fun toggleProjectDefault(team: Team, projectId: Long) = setProjectDefault(projectId, team.id.takeUnless { projectDefaults[projectId] == it })

    fun newRole(team: Team) {
        val form = RoleForm(null, lead = false)
        form.name.edit { replace(0, length, uniqueName(NEW_ROLE_NAME, team)) }
        dialog = RoleDialogState(team.id, null, form)
    }

    fun editRole(team: Team, role: TeamRole) {
        dialog = RoleDialogState(team.id, team.roles.indexOf(role), RoleForm(role.draft))
    }

    fun duplicateRole(team: Team, role: TeamRole) =
        change(team) { it + role.draft.withName(uniqueName(role.name() + COPY_SUFFIX, team)).withLead(false) }

    fun requestDeleteRole(team: Team, role: TeamRole) {
        if (role.lead()) return report("Make another agent the lead before removing \"${role.name()}\".")
        confirm(ConfirmRequest("Remove agent", "Remove \"${role.name()}\" from \"${team.name}\"?", "Remove") {
            change(team) { drafts -> drafts - role.draft }
        })
    }

    fun moveRole(team: Team, role: TeamRole, step: Int) = change(team) { drafts ->
        val from = drafts.indexOf(role.draft)
        val to = from + step
        if (from < 0 || to !in drafts.indices) drafts else drafts.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Saves the dialog's role into its team; the dialog closes on success and [onError] gets the message otherwise. */
    fun saveRole(state: RoleDialogState, onError: (String) -> Unit) {
        val team = find(state.teamId) ?: return closeDialog()
        scope.launch {
            try {
                val saved = state.form.draft()
                withContext(Dispatchers.IO) { service.update(team.id, team.name, withRole(team.drafts(), state.index, saved)) }
                dialog = null
            } catch (e: IllegalArgumentException) {
                onError(e.message ?: "Could not save the agent")
            }
        }
    }

    /** Closes the dialog, first asking when it holds edits. */
    fun requestCloseDialog() {
        val open = dialog ?: return
        if (!open.form.dirty) return closeDialog()
        confirm(ConfirmRequest("Discard changes", "This agent has unsaved changes.", "Discard") { closeDialog() })
    }

    fun closeDialog() {
        dialog = null
    }

    private fun withRole(drafts: List<RoleDraft>, index: Int?, saved: RoleDraft): List<RoleDraft> {
        val replaced = if (index == null) drafts + saved else drafts.toMutableList().apply { set(index, saved) }
        return if (saved.lead) replaced.map { if (it === saved) it else it.withLead(false) } else replaced
    }

    private fun change(team: Team, transform: (List<RoleDraft>) -> List<RoleDraft>) =
        run { withContext(Dispatchers.IO) { service.update(team.id, team.name, transform(team.drafts())) } }

    private fun Team.drafts(): List<RoleDraft> = roles.map { it.draft }

    private fun uniqueName(base: String, team: Team): String {
        val taken = team.roles.map { it.name() }.toSet()
        return generateSequence(1) { it + 1 }.map { if (it == 1) base else "$base-$it" }.map { it.take(RoleDraft.MAX_NAME) }.first { it !in taken }
    }

    private suspend fun reload() {
        val (loaded, defaults) = withContext(Dispatchers.IO) { service.list() to service.projectDefaults() }
        teams = loaded
        projectDefaults = defaults
    }

    private fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: IllegalArgumentException) {
                report(e.message ?: "Could not change the team")
            }
        }
    }
}
