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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.github.krpkbhdn.ccmanager.ui.ide.AddIdeDialog
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ToastHost
import com.github.krpkbhdn.ccmanager.ui.components.trackInputModality
import com.github.krpkbhdn.ccmanager.ui.git.dialog.GitDialogHost
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.palette.CommandPalette
import com.github.krpkbhdn.ccmanager.ui.palette.paletteItems
import com.github.krpkbhdn.ccmanager.ui.picker.FolderPickerDialog
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsDialog
import com.github.krpkbhdn.ccmanager.ui.prompts.PromptDialog
import com.github.krpkbhdn.ccmanager.ui.prompts.PromptsScreen
import com.github.krpkbhdn.ccmanager.ui.prompts.TemplatePickerDialog
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantHelpDialog
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartupDialog
import com.github.krpkbhdn.ccmanager.ui.assistant.QuickRequestDialog
import com.github.krpkbhdn.ccmanager.ui.components.LocalModalScrim
import com.github.krpkbhdn.ccmanager.ui.presets.PresetDialogHost
import com.github.krpkbhdn.ccmanager.ui.presets.apply.ApplyPresetDialog
import com.github.krpkbhdn.ccmanager.ui.presets.apply.PresetOfferDialog
import com.github.krpkbhdn.ccmanager.ui.prompts.ProjectPickerDialog
import com.github.krpkbhdn.ccmanager.ui.proposal.ProposalDialogHost
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.contextUsageView
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantScreen
import com.github.krpkbhdn.ccmanager.ui.sessions.SessionsScreen
import com.github.krpkbhdn.ccmanager.ui.state.usageWindowDetails
import com.github.krpkbhdn.ccmanager.ui.usage.UsageScreen
import com.github.krpkbhdn.ccmanager.ui.usage.projectLabel
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginCommandDialog
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsScreen
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpScreen
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsScreen
import com.github.krpkbhdn.ccmanager.ui.run.RunConfigurationDialog
import com.github.krpkbhdn.ccmanager.ui.teams.RoleDialog
import com.github.krpkbhdn.ccmanager.ui.teams.StartTaskDialog
import com.github.krpkbhdn.ccmanager.ui.author.AuthorCatalogDialog
import com.github.krpkbhdn.ccmanager.ui.author.AuthorReportPanel
import com.github.krpkbhdn.ccmanager.ui.teams.StartTaskActions
import com.github.krpkbhdn.ccmanager.ui.teams.TaskTemplates
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsScreen
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.LocalVoice
import java.net.URI
import com.github.krpkbhdn.ccmanager.ui.settings.SettingsDialog
import java.nio.file.Files
import java.nio.file.Path

private const val SIGNED_OUT_MESSAGE = "Claude is signed out. Run `claude` once in a terminal and sign in."
private const val NOT_A_FOLDER = "Drop a folder, not a file."
private val skeletonSidebar = 264.dp
private val NARROW_WIDTH = 1040.dp

@OptIn(ExperimentalComposeUiApi::class, ExperimentalFoundationApi::class)
@Composable
fun AppRoot(state: AppState, osName: String = System.getProperty("os.name"), modifier: Modifier = Modifier) {
    val dropTarget = rememberCoroutineScope().let { scope -> remember { FolderDropTarget(state, scope) } }
    CompositionLocalProvider(LocalVoice provides state.voice) {
        Box(
            modifier.fillMaxSize().background(colors.background).trackInputModality()
                .dragAndDropTarget(shouldStartDragAndDrop = { it.dragData() is DragData.FilesList }, target = dropTarget),
        ) {
            val installation = state.environment.installation
            Crossfade(installation?.installed, label = "appGate") { installed ->
                when (installed) {
                    null -> AppSkeleton()
                    false -> InstallGate(installation?.problem, state.environment.checkingInstallation, { state.environment.recheckInstallation() }, osName)
                    true -> if (installation != null) MainLayout(state, installation)
                }
            }
            NotificationPanel(state)
            ToastHost(state.notifications.toasts, state::openToast, state.notifications::dismissToast, Modifier.align(Alignment.BottomEnd).padding(bottom = 26.dp))
            if (state.paletteOpen) CommandPalette(paletteItems(state, colors.dark)) { state.paletteOpen = false }
            if (state.shortcutsOpen) ShortcutSheet { state.shortcutsOpen = false }
            (state.route as? Route.Project)?.let { state.gits.of(it.id) }?.let { GitDialogHost(it) }
            state.tasks.projectId?.let { id -> state.projects.all.firstOrNull { it.id == id } }?.let { StartTaskDialog(it, state.teams, startTaskTemplates(state, it), startTaskActions(state, it), state.gits.branchOf(it.id) != null) }
            state.tasks.quickStartProjectId?.let { id -> state.projects.all.firstOrNull { it.id == id } }?.let { QuickStartPicker(state, it) }
            state.tasks.queuePickTarget?.let { (id, sessionId) -> state.projects.all.firstOrNull { it.id == id }?.let { QueuePicker(state, it, sessionId) } }
            state.prompts.dialog?.let { PromptDialog(it, state.prompts, state.projects.all, state.teams.teams) }
            state.runs.dialog?.let { RunConfigurationDialog(it, state.runs, state.dataDir) }
            state.teams.dialog?.let { open -> state.teams.find(open.teamId)?.let { RoleDialog(it, open, state.teams) } }
            state.extensions.projectSettings?.let { ProjectSettingsDialog(it, state.extensions::closeProjectSettings) }
            if (state.settingsDialog.open) SettingsDialog(state)
            CompositionLocalProvider(LocalModalScrim provides !state.settingsDialog.open) {
                state.presets.dialog?.let { PresetDialogHost(it, state.presets, state.dataDir) }
                if (state.applyPreset.pickingProject) ProjectPickerDialog(state.projects.all.filter { !it.group }, { state.teams.defaultTeamOf(it.id) }, state.applyPreset::chooseProject, state.applyPreset::close)
                if (state.applyPreset.project != null) ApplyPresetDialog(state.applyPreset)
            }
            state.presetOffer.offer?.let { PresetOfferDialog(it, state.presets.presets, state.presetOffer) }
            state.extensions.plugins.pending?.let { PluginCommandDialog(it, state.extensions.plugins.job == null, state.extensions.plugins::confirmPending, state.extensions.plugins::dismissPending) }
            state.agents.log?.let { SubagentLogHost(state.agents, { state.now }, it) }
            state.author.catalog?.let { AuthorCatalogDialog(it, state.author) }
            state.author.report?.let { AuthorReportPanel(it, state.author::closeReport) }
            ProposalDialogHost(state)
            state.contextSessionId?.let { id ->
                LaunchedEffect(id) { state.contextBreakdown.load(id, false) }
                ContextUsageDialog(contextUsageView(state.environment.sessionUsage[id]), state.contextBreakdown.loadOf(id), state.contextBreakdown.isLoading(id), state.now, { state.contextBreakdown.load(id, true) }, state.contextShowDetails, { state.contextShowDetails = it }) { state.contextSessionId = null; state.contextShowDetails = false }
            }
            if (state.quickRequestOpen) QuickRequestDialog({ state.assistant.request(it); state.quickRequestOpen = false }, { state.quickRequestOpen = false })
            if (state.assistantStartup.asking) AssistantStartupDialog(state.assistantStartup)
            if (state.assistantHelpOpen) AssistantHelpDialog(state.assistantTools) { state.assistantHelpOpen = false }
            state.confirm?.let { ConfirmDialog(it) { state.confirm = null } }
            state.projects.groupDialog?.let { key(it) { GroupDialog(it, state.projects) } }
            if (state.environment.ideDialogOpen) AddIdeDialog(state.environment)
            state.projects.renaming?.let { RenameDialog(it, { name -> state.projects.rename(it.project, name) }, { state.projects.renaming = null }) }
            if (state.projects.pickerOpen) FolderPickerDialog(initial = null, dataDir = state.dataDir, onDismiss = { state.projects.pickerOpen = false }, onPicked = state.projects::addAt)
            if (dropTarget.hovering) DropOverlay()
        }
    }
}

/** Accepts a dropped folder anywhere in the window and adds it as a project. */
@OptIn(ExperimentalComposeUiApi::class)
private class FolderDropTarget(private val state: AppState, private val scope: CoroutineScope) : DragAndDropTarget {
    var hovering by mutableStateOf(false)

    override fun onEntered(event: DragAndDropEvent) { hovering = true }

    override fun onExited(event: DragAndDropEvent) { hovering = false }

    override fun onEnded(event: DragAndDropEvent) { hovering = false }

    override fun onDrop(event: DragAndDropEvent): Boolean {
        hovering = false
        val dropped = (event.dragData() as? DragData.FilesList)?.readFiles().orEmpty().mapNotNull(::toPath)
        scope.launch {
            val folder = withContext(Dispatchers.IO) { dropped.firstOrNull { Files.isDirectory(it) } }
            if (folder == null) state.notifications.reportError("Could not add project", NOT_A_FOLDER) else state.projects.addAt(folder)
        }
        return dropped.isNotEmpty()
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
    if (route == Route.Prompts) return PromptsScreen(state.prompts, state.projects.all, state.teams.teams, { state.teams.defaultTeamOf(it.id) }, { template, project -> state.tasks.open(project.id, template.id) })
    if (route == Route.Teams) return TeamsScreen(state.teams, state.projects.all)
    if (route == Route.Plugins) return PluginsScreen(state.extensions.plugins)
    if (route == Route.Mcp) return McpScreen(state.extensions.mcp)
    if (route == Route.Skills) return SkillsScreen(state.extensions.skills)
    if (route == Route.Sessions) return SessionsScreen(state, { project, hit ->
        runCatching { Path.of(hit.cwd) }.onSuccess { state.sessions.resume(project.id, hit.claudeSessionId, it) }
            .onFailure { state.notifications.reportError("Could not resume the session", "The recorded folder is not a valid path.") }
    })
    if (route == Route.Usage) return UsageScreen(state.usage, usageWindowDetails(state.environment.rateLimits, state.now), projectName = { id -> projectLabel(id) { state.projects.find(it)?.name } })
    if (route == Route.Assistant) return AssistantScreen(state)
    val project = (route as? Route.Project)?.let { target -> state.projects.all.firstOrNull { it.id == target.id } }
    Crossfade(project?.id, label = "route") { shown ->
        val current = state.projects.all.firstOrNull { it.id == shown }
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

private fun startTaskTemplates(state: AppState, project: Project) = TaskTemplates(
    available = state.prompts.forProject(project.id),
    initialId = state.tasks.templateId,
    values = state.tasks.placeholderValuesOf(project),
)

@Composable
private fun QuickStartPicker(state: AppState, project: Project) =
    TemplatePickerDialog(state.prompts.forProject(project.id), project.id, state.teams.teams, { state.tasks.startFromTemplate(project, it) }, { state.tasks.quickStartProjectId = null })

@Composable
private fun QueuePicker(state: AppState, project: Project, ccmSessionId: String) =
    TemplatePickerDialog(state.prompts.forProject(project.id), project.id, state.teams.teams, { state.tasks.queueFromTemplate(project, ccmSessionId, it) }, { state.tasks.queuePickTarget = null })

private fun startTaskActions(state: AppState, project: Project) = StartTaskActions(
    onStart = { team, task, makeDefault, isolated -> state.tasks.start(project, team, task, makeDefault, isolated) },
    onDismiss = state.tasks::close,
)
