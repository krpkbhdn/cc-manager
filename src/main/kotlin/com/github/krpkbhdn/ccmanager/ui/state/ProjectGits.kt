package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.mutableStateMapOf
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.git.model.GitBackend
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The per-project [GitState]s, created on first use and kept in step with the registered projects. */
class ProjectGits(
    private val git: GitBackend,
    private val scope: CoroutineScope,
    private val host: GitHost,
    private val projectOf: (Long) -> Project?,
) {
    private val states = mutableStateMapOf<Long, GitState>()

    /** Git state of a project, created on first use; null for an unknown project or a group (its directory is no repository). */
    fun of(projectId: Long): GitState? {
        states[projectId]?.let { return it }
        val project = projectOf(projectId)?.takeIf { !it.group } ?: return null
        return GitState(projectId, project.path, git, scope, host).also { states[projectId] = it }
    }

    fun summaryOf(projectId: Long): GitSummary? = states[projectId]?.summary

    /** Branch of the project's repository when its git state is already loaded. */
    fun branchOf(projectId: Long): String? = of(projectId)?.status?.branch?.takeIf { it.isNotBlank() }

    fun refresh(projectId: Long) {
        states[projectId]?.refresh()
    }

    /** Loads git state of projects not seen yet, so the sidebar shows branches before a project is opened. */
    fun loadNew(projectIds: Set<Long>) = loadOneByOne(projectIds.filter { it !in states }.mapNotNull(::of))

    /** Local state only (no fetch): the open project has its watcher, the others would otherwise go stale. */
    fun reloadUnopened(openProjectId: Long?) = loadOneByOne(states.filterKeys { it != openProjectId }.values.toList())

    fun retain(projectIds: Set<Long>) {
        states.keys.filter { it !in projectIds }.forEach { states.remove(it)?.close() }
    }

    /** One project at a time, so many projects do not start dozens of git processes at once. */
    private fun loadOneByOne(loaded: List<GitState>) {
        if (loaded.isNotEmpty()) scope.launch { loaded.forEach { it.reloadNow() } }
    }
}
