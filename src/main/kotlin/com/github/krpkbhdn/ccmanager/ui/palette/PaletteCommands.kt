package com.github.krpkbhdn.ccmanager.ui.palette

import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.run.RunEntry
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
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
    val shortcut: AppShortcut? = null,
    val run: () -> Unit,
)

/** Everything the palette can jump to or do right now, derived from the live app state. */
fun paletteItems(state: AppState, systemDark: Boolean): List<PaletteItem> =
    sessionItems(state) + projectItems(state) + globalActions(state, systemDark) + projectActions(state) + gitActions(state)

private fun sessionItems(state: AppState): List<PaletteItem> =
    byUrgency(state.live.sessions).map { session ->
        PaletteItem(
            id = "session-${session.ccmSessionId}",
            title = sessionTitle(session),
            group = PaletteGroup.SESSIONS,
            subtitle = state.projects.nameOf(session.projectId),
            status = session.status,
            run = { state.openSession(session.ccmSessionId) },
        )
    }

private fun projectItems(state: AppState): List<PaletteItem> =
    state.projects.all.map { project ->
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
    PaletteItem("add-project", "Add project", PaletteGroup.ACTIONS, "Pick a folder to manage", Icons.FolderPlus, shortcut = AppShortcut.ADD_PROJECT, run = state::requestAddProject),
    PaletteItem("overview", "Go to overview", PaletteGroup.ACTIONS, icon = Icons.Grid, shortcut = AppShortcut.OVERVIEW) { state.navigate(Route.Overview) },
    PaletteItem("sessions", "All sessions", PaletteGroup.ACTIONS, "Every session of every project", Icons.Layers, shortcut = AppShortcut.ALL_SESSIONS) { state.navigate(Route.Sessions) },
    PaletteItem("next-waiting", "Answer next waiting session", PaletteGroup.ACTIONS, "Jump to the session that needs you", Icons.Message, shortcut = AppShortcut.NEXT_WAITING) { state.answerNextWaiting() },
    PaletteItem("teams", "Manage teams", PaletteGroup.ACTIONS, "Roles and models of your agent teams", Icons.Users) { state.navigate(Route.Teams) },
    PaletteItem("prompts", "Templates", PaletteGroup.ACTIONS, "Reusable task texts", Icons.Message) { state.navigate(Route.Prompts) },
    PaletteItem("author-config", "Author configuration", PaletteGroup.ACTIONS, "Install or update the author's Claude setup", Icons.Sparkles) { state.author.openManage((state.route as? Route.Project)?.id) },
    PaletteItem("plugins", "Open plugins", PaletteGroup.ACTIONS, "Install and manage Claude plugins", Icons.Plug) { state.navigate(Route.Plugins) },
    PaletteItem("mcp", "Open MCP servers", PaletteGroup.ACTIONS, "Servers that give Claude extra tools", Icons.Server) { state.navigate(Route.Mcp) },
    PaletteItem("skills", "Open skills", PaletteGroup.ACTIONS, "Instructions Claude loads on demand", Icons.Book) { state.navigate(Route.Skills) },
    PaletteItem("notifications", "Show notifications", PaletteGroup.ACTIONS, icon = Icons.Bell, shortcut = AppShortcut.NOTIFICATIONS) { state.notificationsOpen = true },
    PaletteItem("usage", "Show usage", PaletteGroup.ACTIONS, "Limits, context and cost", Icons.Gauge, shortcut = AppShortcut.USAGE) { state.usageOpen = true },
    PaletteItem("toggle-sound", "Toggle notification sound", PaletteGroup.ACTIONS, if (state.settings.notificationSound) "Currently on" else "Currently off", Icons.Bell) { state.toggleNotificationSound() },
    PaletteItem("toggle-theme", "Toggle light and dark theme", PaletteGroup.ACTIONS, icon = Icons.Sun, shortcut = AppShortcut.TOGGLE_THEME) { state.toggleTheme(systemDark) },
    PaletteItem("system-theme", "Use the system theme", PaletteGroup.ACTIONS, icon = Icons.Monitor) { state.setThemeMode(ThemeMode.SYSTEM) },
    PaletteItem("shortcuts", "Keyboard shortcuts", PaletteGroup.ACTIONS, icon = Icons.Command, shortcut = AppShortcut.SHORTCUT_SHEET) { state.shortcutsOpen = true },
    PaletteItem("toggle-sidebar", "Toggle sidebar", PaletteGroup.ACTIONS, icon = Icons.PanelLeftClose, shortcut = AppShortcut.TOGGLE_SIDEBAR, run = state::toggleSidebar),
)

private fun shellActions(state: AppState, projectId: Long, here: String): List<PaletteItem> {
    val default = state.shells.default ?: return emptyList()
    val openDefault = PaletteItem("open-terminal", "Open terminal", PaletteGroup.ACTIONS, "$here: ${default.name}", Icons.SquareTerminal, shortcut = AppShortcut.OPEN_TERMINAL) { state.shells.open(projectId, default) }
    val perShell = state.shells.available.map { shell ->
        PaletteItem("open-terminal-${shell.name}", "Open terminal: ${shell.name}", PaletteGroup.ACTIONS, here, Icons.SquareTerminal) { state.shells.open(projectId, shell) }
    }
    return listOf(openDefault) + perShell
}

private fun projectActions(state: AppState): List<PaletteItem> {
    val route = state.route as? Route.Project ?: return emptyList()
    val project = state.projects.all.firstOrNull { it.id == route.id } ?: return emptyList()
    val here = "in ${project.name}"
    return shellActions(state, project.id, here) + listOfNotNull(
        PaletteItem("launch", "Launch Claude", PaletteGroup.ACTIONS, here, Icons.Play, shortcut = AppShortcut.NEW_SESSION) { state.sessions.launch(project.id, LaunchMode.NEW) },
        PaletteItem("start-task", "Start task with team", PaletteGroup.ACTIONS, here, Icons.Users) { state.openTaskDialog(project.id) },
        PaletteItem("continue", "Continue last session", PaletteGroup.ACTIONS, here, Icons.ArrowRight) { state.sessions.launch(project.id, LaunchMode.CONTINUE) },
        PaletteItem("resume", "Resume a session", PaletteGroup.ACTIONS, here, Icons.Rewind) { state.sessions.launch(project.id, LaunchMode.RESUME_PICKER) },
        PaletteItem("open-folder", "Open folder", PaletteGroup.ACTIONS, here, Icons.FolderOpen) { state.openFolder(project) },
        PaletteItem("open-ide", "Open in IntelliJ IDEA", PaletteGroup.ACTIONS, here, Icons.Code) { state.openIde(project) },
        PaletteItem("project-settings", "Project settings", PaletteGroup.ACTIONS, "$here: plugins, MCP servers, skills", Icons.Settings, shortcut = AppShortcut.PROJECT_SETTINGS) { state.openProjectSettings(project) },
        state.runs.selectedOf(project.id)?.let { runItem(state, it, here) },
        state.runs.selectedOf(project.id)?.takeIf { it.running }?.let { entry -> PaletteItem("stop-run", "Stop ${entry.name}", PaletteGroup.ACTIONS, here, Icons.Stop) { state.runs.stop(entry.id) } },
        PaletteItem("show-run", "Show run console", PaletteGroup.ACTIONS, here, Icons.SquareTerminal) { state.select(project.id, ProjectTab.Run) },
        PaletteItem("show-activity", "Show activity", PaletteGroup.ACTIONS, here, Icons.Activity, shortcut = AppShortcut.ACTIVITY_TAB) { state.select(project.id, ProjectTab.Activity) },
        PaletteItem("toggle-agents", "Toggle agents panel", PaletteGroup.ACTIONS, here, Icons.PanelRightClose, shortcut = AppShortcut.TOGGLE_AGENTS, run = state::toggleAgents),
        state.viewOf(project.id).focusedSession?.let { state.live.find(it) }?.let { session ->
            PaletteItem("switch-model", "Switch model", PaletteGroup.ACTIONS, "$here, opens Claude's picker (Alt+P)", Icons.Cpu) { state.sessions.openClaudeModelPicker(session) }
        },
    )
}

private fun runItem(state: AppState, entry: RunEntry, here: String) =
    PaletteItem("run", (if (entry.running) "Restart " else "Run ") + entry.name, PaletteGroup.ACTIONS, here, Icons.Play, shortcut = AppShortcut.RUN) { state.runs.run(entry.id) }
