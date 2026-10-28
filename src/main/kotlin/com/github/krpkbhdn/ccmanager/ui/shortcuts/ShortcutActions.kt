package com.github.krpkbhdn.ccmanager.ui.shortcuts

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.push
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.Route

/** Runs [AppShortcut]s against the app state; returns false when a shortcut does not apply right now so the key stays untouched. */
class ShortcutActions(private val state: AppState, private val systemDark: () -> Boolean) {

    fun perform(shortcut: AppShortcut): Boolean {
        if (shortcut == AppShortcut.CLOSE_OVERLAY) return closeOverlay()
        if (modalOpen()) return false
        shortcut.projectSlot?.let { return openProject(it) }
        return when (shortcut) {
            AppShortcut.PALETTE -> handled { state.paletteOpen = !state.paletteOpen }
            AppShortcut.ADD_PROJECT -> handled(state::requestAddProject)
            AppShortcut.TOGGLE_SIDEBAR -> handled(state::toggleSidebar)
            AppShortcut.TOGGLE_AGENTS -> handled(state::toggleAgents)
            AppShortcut.TOGGLE_QUICK_ACTIONS -> handled(state::toggleQuickBar)
            AppShortcut.TOGGLE_THEME -> handled { state.toggleTheme(systemDark()) }
            AppShortcut.OVERVIEW -> handled { state.navigate(Route.Overview) }
            AppShortcut.ALL_SESSIONS -> handled { state.navigate(Route.Sessions) }
            AppShortcut.NEXT_WAITING -> state.answerNextWaiting()
            AppShortcut.NOTIFICATIONS -> handled { state.notificationsOpen = !state.notificationsOpen }
            AppShortcut.USAGE -> handled { state.usageOpen = !state.usageOpen }
            AppShortcut.SHORTCUT_SHEET -> handled { state.shortcutsOpen = !state.shortcutsOpen }
            else -> performInProject(shortcut)
        }
    }

    private fun performInProject(shortcut: AppShortcut): Boolean {
        val project = currentProject() ?: return false
        if (project.group && shortcut in PROJECT_ONLY) return false
        val view = state.viewOf(project.id)
        val session = (view.selected as? ProjectTab.Session)?.ccmSessionId
        return when (shortcut) {
            AppShortcut.NEW_SESSION -> handled { state.sessions.launch(project.id, LaunchMode.NEW) }
            AppShortcut.OPEN_TERMINAL -> state.shells.default != null && handled { state.shells.openDefault(project.id) }
            AppShortcut.CLOSE_SESSION -> closeSelectedTab(project.id, view.selected)
            AppShortcut.NEXT_TAB -> handled { state.select(project.id, view.cycle(1).selected) }
            AppShortcut.PREVIOUS_TAB -> handled { state.select(project.id, view.cycle(-1).selected) }
            AppShortcut.GIT_TAB -> handled { state.openGit(project.id) }
            AppShortcut.GIT_CHANGES -> handled { state.openGit(project.id, GitSubView.CHANGES) }
            AppShortcut.GIT_COMMIT -> handled { state.openGit(project.id)?.requestCommitFocus() }
            AppShortcut.GIT_BRANCHES -> handled { state.openGit(project.id)?.branchPopupOpen = true }
            AppShortcut.GIT_PUSH -> handled { state.gitOf(project.id)?.push() }
            AppShortcut.PROJECT_SETTINGS -> handled { state.openProjectSettings(project) }
            AppShortcut.RUN -> state.runs.selectedOf(project.id)?.takeIf { !it.directoryMissing }?.let { entry -> handled { state.runs.run(entry.id) } } ?: false
            AppShortcut.ACTIVITY_TAB -> handled { state.select(project.id, ProjectTab.Activity) }
            AppShortcut.FOCUS_TERMINAL -> session != null && handled { state.terminal.focus(session) }
            else -> false
        }
    }

    private fun closeSelectedTab(projectId: Long, tab: ProjectTab): Boolean = when (tab) {
        is ProjectTab.Session -> handled { state.sessions.requestClose(projectId, tab.ccmSessionId) }
        is ProjectTab.Shell -> handled { state.shells.close(projectId, tab.terminalId) }
        else -> false
    }

    private fun openProject(slot: Int): Boolean {
        val project = sidebarOrder().getOrNull(slot - 1) ?: return false
        state.navigate(Route.Project(project.id))
        return true
    }

    internal fun sidebarOrder(): List<Project> {
        val projects = state.projects
        val railShowsAll = state.settings.sidebarCollapsed
        return projects.groups.flatMap { group ->
            if (railShowsAll || group.id !in projects.foldedGroups) listOf(group) + projects.membersOf(group.id) else listOf(group)
        } + projects.ungrouped
    }

    private fun closeOverlay(): Boolean {
        val open = listOf(state::shortcutsOpen, state::paletteOpen, state::notificationsOpen, state::usageOpen).firstOrNull { it.get() } ?: return false
        open.set(false)
        return true
    }

    private fun currentProject(): Project? = (state.route as? Route.Project)?.let { route -> state.projects.all.firstOrNull { it.id == route.id } }

    private fun modalOpen() = state.proposalDialog != null || state.confirm != null || state.projects.renaming != null || state.projects.groupDialog != null || state.projects.pickerOpen || state.projectSettings != null ||
        state.taskDialogProjectId != null || state.quickStartProjectId != null || state.queuePickTarget != null ||state.runs.dialog != null || state.teams.dialog != null || state.agents.log != null || state.plugins.pending != null || state.mcp.adding || state.skills.creating || state.author.offer != null || state.author.manage != null || state.author.report != null ||
        currentProject()?.let { state.gitOf(it.id)?.dialog } != null

    private fun handled(action: () -> Unit): Boolean {
        action()
        return true
    }
}

private val PROJECT_ONLY = setOf(
    AppShortcut.OPEN_TERMINAL, AppShortcut.GIT_TAB, AppShortcut.GIT_CHANGES, AppShortcut.GIT_COMMIT,
    AppShortcut.GIT_BRANCHES, AppShortcut.GIT_PUSH, AppShortcut.RUN, AppShortcut.PROJECT_SETTINGS,
)
