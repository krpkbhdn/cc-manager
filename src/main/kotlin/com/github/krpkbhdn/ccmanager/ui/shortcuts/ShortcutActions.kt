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
            AppShortcut.NOTIFICATIONS -> handled { state.notificationsOpen = !state.notificationsOpen }
            AppShortcut.SHORTCUT_SHEET -> handled { state.shortcutsOpen = !state.shortcutsOpen }
            else -> performInProject(shortcut)
        }
    }

    private fun performInProject(shortcut: AppShortcut): Boolean {
        val project = currentProject() ?: return false
        val view = state.viewOf(project.id)
        val session = (view.selected as? ProjectTab.Session)?.ccmSessionId
        return when (shortcut) {
            AppShortcut.NEW_SESSION -> handled { state.launch(project.id, LaunchMode.NEW) }
            AppShortcut.CLOSE_SESSION -> session != null && handled { state.requestCloseSession(project.id, session) }
            AppShortcut.NEXT_TAB -> handled { state.select(project.id, view.cycle(1).selected) }
            AppShortcut.PREVIOUS_TAB -> handled { state.select(project.id, view.cycle(-1).selected) }
            AppShortcut.GIT_TAB -> handled { state.openGit(project.id) }
            AppShortcut.GIT_CHANGES -> handled { state.openGit(project.id, GitSubView.CHANGES) }
            AppShortcut.GIT_COMMIT -> handled { state.openGit(project.id)?.requestCommitFocus() }
            AppShortcut.GIT_BRANCHES -> handled { state.openGit(project.id)?.branchPopupOpen = true }
            AppShortcut.GIT_PUSH -> handled { state.gitOf(project.id)?.push() }
            AppShortcut.ACTIVITY_TAB -> handled { state.select(project.id, ProjectTab.Activity) }
            AppShortcut.FOCUS_TERMINAL -> session != null && handled { state.terminal.focus(session) }
            else -> false
        }
    }

    private fun openProject(slot: Int): Boolean {
        val project = state.projects.getOrNull(slot - 1) ?: return false
        state.navigate(Route.Project(project.id))
        return true
    }

    private fun closeOverlay(): Boolean {
        val open = listOf(state::shortcutsOpen, state::paletteOpen, state::notificationsOpen).firstOrNull { it.get() } ?: return false
        open.set(false)
        return true
    }

    private fun currentProject(): Project? = (state.route as? Route.Project)?.let { route -> state.projects.firstOrNull { it.id == route.id } }

    private fun modalOpen() = state.confirm != null || state.renaming != null || state.pickerOpen

    private fun handled(action: () -> Unit): Boolean {
        action()
        return true
    }
}
