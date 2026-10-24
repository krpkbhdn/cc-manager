package com.github.krpkbhdn.ccmanager.ui.palette

import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.ThemeMode
import com.github.krpkbhdn.ccmanager.ui.state.byUrgency
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle

enum class PaletteGroup(val title: String) { SESSIONS("Sessions"), PROJECTS("Projects"), ACTIONS("Actions") }

/** One row of the command palette: [avatarOf] draws a project avatar, [status] a traffic light, otherwise [icon]. */
data class PaletteItem(
    val id: String,
    val title: String,
    val group: PaletteGroup,
    val subtitle: String = "",
    val icon: ImageVector? = null,
    val avatarOf: String? = null,
    val status: AgentStatus? = null,
    val shortcut: String? = null,
    val run: () -> Unit,
)

/** Everything the palette can jump to or do right now, derived from the live app state. */
fun paletteItems(state: AppState, systemDark: Boolean): List<PaletteItem> =
    sessionItems(state) + projectItems(state) + globalActions(state, systemDark) + projectActions(state)

private fun sessionItems(state: AppState): List<PaletteItem> =
    byUrgency(state.live.sessions).map { session ->
        PaletteItem(
            id = "session-${session.ccmSessionId}",
            title = sessionTitle(session),
            group = PaletteGroup.SESSIONS,
            subtitle = state.projectName(session.projectId),
            status = session.status,
            run = { state.openSession(session.ccmSessionId) },
        )
    }

private fun projectItems(state: AppState): List<PaletteItem> =
    state.projects.map { project ->
        PaletteItem(
            id = "project-${project.id}",
            title = project.name,
            group = PaletteGroup.PROJECTS,
            subtitle = project.path.toString(),
            avatarOf = project.name,
            run = { state.navigate(Route.Project(project.id)) },
        )
    }

private fun globalActions(state: AppState, systemDark: Boolean): List<PaletteItem> = listOf(
    PaletteItem("add-project", "Add project", PaletteGroup.ACTIONS, "Pick a folder to manage", Icons.FolderPlus, shortcut = "Ctrl N", run = state::requestAddProject),
    PaletteItem("overview", "Go to overview", PaletteGroup.ACTIONS, icon = Icons.Grid) { state.navigate(Route.Overview) },
    PaletteItem("notifications", "Show notifications", PaletteGroup.ACTIONS, icon = Icons.Bell) { state.notificationsOpen = true },
    PaletteItem("toggle-theme", "Toggle light and dark theme", PaletteGroup.ACTIONS, icon = Icons.Sun) { state.toggleTheme(systemDark) },
    PaletteItem("system-theme", "Use the system theme", PaletteGroup.ACTIONS, icon = Icons.Monitor) { state.setThemeMode(ThemeMode.SYSTEM) },
    PaletteItem("toggle-sidebar", "Toggle sidebar", PaletteGroup.ACTIONS, icon = Icons.PanelLeftClose, shortcut = "Ctrl B", run = state::toggleSidebar),
)

private fun projectActions(state: AppState): List<PaletteItem> {
    val route = state.route as? Route.Project ?: return emptyList()
    val project = state.projects.firstOrNull { it.id == route.id } ?: return emptyList()
    val here = "in ${project.name}"
    return listOf(
        PaletteItem("launch", "Launch Claude", PaletteGroup.ACTIONS, here, Icons.Play) { state.launch(project.id, LaunchMode.NEW) },
        PaletteItem("continue", "Continue last session", PaletteGroup.ACTIONS, here, Icons.ArrowRight) { state.launch(project.id, LaunchMode.CONTINUE) },
        PaletteItem("resume", "Resume a session", PaletteGroup.ACTIONS, here, Icons.Rewind) { state.launch(project.id, LaunchMode.RESUME_PICKER) },
        PaletteItem("open-folder", "Open folder", PaletteGroup.ACTIONS, here, Icons.FolderOpen) { state.openFolder(project) },
        PaletteItem("open-ide", "Open in IntelliJ IDEA", PaletteGroup.ACTIONS, here, Icons.Code) { state.openIde(project) },
        PaletteItem("show-diff", "Show diff", PaletteGroup.ACTIONS, here, Icons.Diff) { state.select(project.id, ProjectTab.Diff) },
        PaletteItem("show-activity", "Show activity", PaletteGroup.ACTIONS, here, Icons.Activity) { state.select(project.id, ProjectTab.Activity) },
        PaletteItem("toggle-agents", "Toggle agents panel", PaletteGroup.ACTIONS, here, Icons.PanelRightClose, shortcut = "Ctrl J", run = state::toggleAgents),
    )
}
