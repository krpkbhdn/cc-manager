package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.git.TaskWorktreeService
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderSubstitution
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderValues
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.prompts.teamForTemplate
import com.github.krpkbhdn.ccmanager.ui.queue.QueueState
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The start-task dialog and the two template pickers (quick start, queue), and what they do with the chosen text. */
class TaskDialogState(
    private val sessions: SessionsState,
    private val teams: TeamsState,
    private val queue: QueueState,
    private val scope: CoroutineScope,
    private val taskWorktrees: TaskWorktreeService,
    private val reportError: (String) -> Unit,
    private val branchOf: (Long) -> String?,
    private val branchOfSession: (projectId: Long, ccmSessionId: String) -> String? = { projectId, _ -> branchOf(projectId) },
    private val onWorktreeCreated: (projectId: Long) -> Unit = {},
) {
    /** True while an isolated task is being set up, so a second Enter cannot create a second worktree. */
    var starting by mutableStateOf(false)
        private set
    var projectId by mutableStateOf<Long?>(null)
    var templateId by mutableStateOf<Long?>(null)
    var quickStartProjectId by mutableStateOf<Long?>(null)

    /** Project and live session whose queue receives the card chosen in the picker. */
    var queuePickTarget by mutableStateOf<Pair<Long, String>?>(null)

    fun open(projectId: Long, templateId: Long? = null) {
        this.templateId = templateId
        this.projectId = projectId
    }

    fun close() {
        projectId = null
        templateId = null
    }

    fun placeholderValuesOf(project: Project) = PlaceholderValues(project.name, branchOf(project.id), null)

    /** The branch is the one of the folder the session runs in, which may be a worktree. */
    private fun placeholderValuesOf(project: Project, ccmSessionId: String) = PlaceholderValues(project.name, branchOfSession(project.id, ccmSessionId), null)

    fun queueFromTemplate(project: Project, ccmSessionId: String, template: PromptTemplate) {
        queuePickTarget = null
        queue.add(ccmSessionId, PlaceholderSubstitution.apply(template.text, placeholderValuesOf(project, ccmSessionId)))
    }

    /** Starts [template] at once in [project], led by its default team or else the project's default team. */
    fun startFromTemplate(project: Project, template: PromptTemplate) {
        quickStartProjectId = null
        val team = teamForTemplate(template, teams.teams, teams.defaultTeamOf(project.id))
        start(project, team, PlaceholderSubstitution.apply(template.text, placeholderValuesOf(project)).trim(), false, false)
    }

    /** Starts a session with [task] as its first prompt, led by [team] or without a team, and opens its terminal. */
    fun start(project: Project, team: Team?, task: String, makeDefault: Boolean, isolated: Boolean) {
        val onStarted = {
            close()
            if (makeDefault && team != null) teams.setProjectDefault(project.id, team.id)
        }
        if (isolated) startInWorktree(project, team, task, onStarted) else sessions.launchTask(project.id, team, task, onStarted = { onStarted() })
    }

    /**
     * Creates branch task/<slug> in a new worktree and runs the task session there under the same project; further starts are ignored until this one ends, and a failure after the worktree exists says where it is.
     */
    private fun startInWorktree(project: Project, team: Team?, task: String, onStarted: () -> Unit) {
        if (starting) return
        starting = true
        scope.launch {
            try {
                runGuarded({ starting = false; reportError(it) }) {
                    val created = io { taskWorktrees.create(project.id, project.path, task) }
                    onWorktreeCreated(project.id)
                    sessions.launchTaskIn(project.id, created.path(), team, task, { reason -> starting = false; reportError(strandedWorktreeMessage(created, reason)) }) { starting = false; onStarted() }
                }
            } catch (e: Throwable) {
                starting = false
                if (e !is CancellationException) reportError(e.message ?: "The task could not be started")
                throw e
            }
        }
    }
}

/** The worktree stays on disk when a later step fails, so the user must be told where it is. */
fun strandedWorktreeMessage(worktree: TaskWorktree, reason: String) =
    "$reason. The worktree ${worktree.path()} on branch ${worktree.branch()} was created and is still there; remove it in the Git tab's Worktrees view if you do not need it."
