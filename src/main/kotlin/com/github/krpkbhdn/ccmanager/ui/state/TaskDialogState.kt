package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderSubstitution
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderValues
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.prompts.teamForTemplate
import com.github.krpkbhdn.ccmanager.ui.queue.QueueState
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsState

/** The start-task dialog and the two template pickers (quick start, queue), and what they do with the chosen text. */
class TaskDialogState(
    private val sessions: SessionsState,
    private val teams: TeamsState,
    private val queue: QueueState,
    private val branchOf: (Long) -> String?,
) {
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

    fun queueFromTemplate(project: Project, ccmSessionId: String, template: PromptTemplate) {
        queuePickTarget = null
        queue.add(ccmSessionId, PlaceholderSubstitution.apply(template.text, placeholderValuesOf(project)))
    }

    /** Starts [template] at once in [project], led by its default team or else the project's default team. */
    fun startFromTemplate(project: Project, template: PromptTemplate) {
        quickStartProjectId = null
        val team = teamForTemplate(template, teams.teams, teams.defaultTeamOf(project.id))
        start(project, team, PlaceholderSubstitution.apply(template.text, placeholderValuesOf(project)).trim(), false)
    }

    /** Starts a session with [task] as its first prompt, led by [team] or without a team, and opens its terminal. */
    fun start(project: Project, team: Team?, task: String, makeDefault: Boolean) {
        sessions.launchTask(project.id, team, task) {
            close()
            if (makeDefault && team != null) teams.setProjectDefault(project.id, team.id)
        }
    }
}
