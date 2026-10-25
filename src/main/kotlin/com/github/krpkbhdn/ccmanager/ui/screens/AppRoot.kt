package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.install.LoginState
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.chrome.StatusBar
import com.github.krpkbhdn.ccmanager.ui.chrome.UpdateBanner
import com.github.krpkbhdn.ccmanager.ui.components.Banner
import com.github.krpkbhdn.ccmanager.ui.components.ConfirmDialog
import com.github.krpkbhdn.ccmanager.ui.components.DropOverlay
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.RenameDialog
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ToastHost
import com.github.krpkbhdn.ccmanager.ui.components.trackInputModality
import com.github.krpkbhdn.ccmanager.ui.git.dialog.GitDialogHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.palette.CommandPalette
import com.github.krpkbhdn.ccmanager.ui.palette.paletteItems
import com.github.krpkbhdn.ccmanager.ui.picker.FolderPickerDialog
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsDialog
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginCommandDialog
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsScreen
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpScreen
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsScreen
import com.github.krpkbhdn.ccmanager.ui.teams.RoleDialog
import com.github.krpkbhdn.ccmanager.ui.teams.StartTaskDialog
import com.github.krpkbhdn.ccmanager.ui.teams.StartTaskActions
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsScreen
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.net.URI
import java.nio.file.Files
import java.nio.file.Path

private const val SIGNED_OUT_MESSAGE = "Claude is signed out. Run `claude` once in a terminal and sign in."
private const val NOT_A_FOLDER = "Drop a folder, not a file."
private val skeletonSidebar = 264.dp
private val NARROW_WIDTH = 1040.dp

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppRoot(state: AppState, osName: String = System.getProperty("os.name"), modifier: Modifier = Modifier) {
    val dropTarget = remember { FolderDropTarget(state) }
    Box(
        modifier.fillMaxSize().background(colors.background).trackInputModality()
            .dragAndDropTarget(shouldStartDragAndDrop = { it.dragData() is DragData.FilesList }, target = dropTarget),
    ) {
        val installation = state.installation
        Crossfade(installation?.installed, label = "appGate") { installed ->
            when (installed) {
                null -> AppSkeleton()
                false -> InstallGate(installation?.problem, state.checkingInstallation, { state.recheckInstallation() }, osName)
                true -> if (installation != null) MainLayout(state, installation)
            }
        }
        NotificationPanel(state)
        ToastHost(state.toasts, state::openToast, state::dismissToast, Modifier.align(Alignment.BottomEnd).padding(bottom = 26.dp))
        if (state.paletteOpen) CommandPalette(paletteItems(state, colors.dark)) { state.paletteOpen = false }
        if (state.shortcutsOpen) ShortcutSheet { state.shortcutsOpen = false }
        (state.route as? Route.Project)?.let { state.gitOf(it.id) }?.let { GitDialogHost(it) }
        state.taskDialogProjectId?.let { id -> state.projects.firstOrNull { it.id == id } }?.let { StartTaskDialog(it, state.teams, startTaskActions(state, it)) }
        state.teams.dialog?.let { open -> state.teams.find(open.teamId)?.let { RoleDialog(it, open, state.teams) } }
        state.projectSettings?.let { ProjectSettingsDialog(it, state::closeProjectSettings) }
        state.plugins.pending?.let { PluginCommandDialog(it, state.plugins.job == null, state.plugins::confirmPending, state.plugins::dismissPending) }
        state.agents.log?.let { SubagentLogHost(state.agents, { state.now }, it) }
        state.confirm?.let { ConfirmDialog(it) { state.confirm = null } }
        state.renaming?.let { RenameDialog(it, { name -> state.renameProject(it.project, name) }, { state.renaming = null }) }
        if (state.pickerOpen) FolderPickerDialog(initial = null, onDismiss = { state.pickerOpen = false }, onPicked = state::addProjectAt)
        if (dropTarget.hovering) DropOverlay()
    }
}

/** Accepts a dropped folder anywhere in the window and adds it as a project. */
@OptIn(ExperimentalComposeUiApi::class)
private class FolderDropTarget(private val state: AppState) : DragAndDropTarget {
    var hovering by mutableStateOf(false)

    override fun onEntered(event: DragAndDropEvent) { hovering = true }

    override fun onExited(event: DragAndDropEvent) { hovering = false }

    override fun onEnded(event: DragAndDropEvent) { hovering = false }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        hovering = false
        val dropped = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty().mapNotNull(::toPath)
        val folder = dropped.firstOrNull { Files.isDirectory(it) }
        if (folder == null) {
            state.reportError("Could not add project", NOT_A_FOLDER)
            return false
        }
        state.addProjectAt(folder)
        return true
    }

    private fun toPath(uri: String): Path? = runCatching { Path.of(URI(uri)) }.getOrNull()
}

@Composable
private fun MainLayout(state: AppState, installation: ClaudeInstallation) = BoxWithConstraints(Modifier.fillMaxSize()) {
    val narrow = maxWidth < NARROW_WIDTH
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Sidebar(state, forceCollapsed = narrow)
            Column(Modifier.weight(1f).fillMaxSize()) {
                if (installation.login == LoginState.LOGGED_OUT && !state.loginBannerDismissed) {
                    Banner(SIGNED_OUT_MESSAGE, colors.warning, Icons.Alert) { IconButton(Icons.Close, { state.loginBannerDismissed = true }, tooltip = "Dismiss") }
                }
                UpdateBanner(state.update)
                RouteContent(state)
            }
        }
        StatusBar(state)
    }
}

@Composable
private fun RouteContent(state: AppState) {
    val route = state.route
    if (route == Route.Teams) return TeamsScreen(state.teams, state.projects)
    if (route == Route.Plugins) return PluginsScreen(state.plugins)
    if (route == Route.Mcp) return McpScreen(state.mcp)
    if (route == Route.Skills) return SkillsScreen(state.skills)
    val project = (route as? Route.Project)?.let { target -> state.projects.firstOrNull { it.id == target.id } }
    Crossfade(project?.id, label = "route") { shown ->
        val current = state.projects.firstOrNull { it.id == shown }
        if (current != null) ProjectScreen(state, current) else OverviewScreen(state)
    }
}

@Composable
private fun AppSkeleton() {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(skeletonSidebar).fillMaxSize().background(colors.sidebar).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(SKELETON_ROWS) { SkeletonBlock(Modifier.fillMaxWidth().height(40.dp)) }
        }
        LazyVerticalGrid(GridCells.Adaptive(320.dp), Modifier.fillMaxSize().alpha(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(28.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            items(SKELETON_CARDS) { SkeletonBlock(Modifier.fillMaxWidth().height(168.dp), androidx.compose.foundation.shape.RoundedCornerShape(18.dp)) }
        }
    }
}

private const val SKELETON_ROWS = 6
private const val SKELETON_CARDS = 6

private fun startTaskActions(state: AppState, project: Project) = StartTaskActions(
    onStart = { team, task, makeDefault -> state.startTask(project, team, task, makeDefault) },
    onManageTeams = { state.navigate(Route.Teams) },
    onDismiss = { state.taskDialogProjectId = null },
)
