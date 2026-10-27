package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgent
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderSubstitution
import com.github.krpkbhdn.ccmanager.prompt.PlaceholderValues
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.author.AuthorProfileState
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsState
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsSection
import com.github.krpkbhdn.ccmanager.ui.projectsettings.ProjectSettingsState
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.HealthCheck
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillHost
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsState
import com.github.krpkbhdn.ccmanager.ui.proposal.ProposalState
import com.github.krpkbhdn.ccmanager.ui.prompts.PromptsState
import com.github.krpkbhdn.ccmanager.ui.prompts.teamForTemplate
import com.github.krpkbhdn.ccmanager.ui.queue.QueueState
import com.github.krpkbhdn.ccmanager.ui.run.RunState
import com.github.krpkbhdn.ccmanager.ui.sessions.nextWaiting
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsState
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val TICK_MILLIS = 1_000L
private const val STALE_REFRESH_TICKS = 30
private const val ACTIVITY_LIMIT = 300

/**
 * Single subscription point to the backend: re-reads snapshots on change callbacks (any thread) and exposes
 * Compose state. All state writes happen on [scope], which must run on the UI dispatcher.
 */
class AppState(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val settingsStore: UiSettingsStore = UiSettingsStore.inDataDir(backend.dataDir),
) {
    var settings by mutableStateOf(settingsStore.load())
        private set
    var paletteOpen by mutableStateOf(false)
    var shortcutsOpen by mutableStateOf(false)

    var installation by mutableStateOf<ClaudeInstallation?>(null)
        private set
    var checkingInstallation by mutableStateOf(false)
        private set
    var ideAvailable by mutableStateOf(false)
        private set
    var rateLimits by mutableStateOf<RateLimitSnapshot?>(null)
        private set
    var live by mutableStateOf(LiveSessions())
        private set
    val workingStaleAfter: Duration = backend.sessionProperties.workingStaleAfter()
    var now by mutableStateOf(Instant.now())
        private set
    var route by mutableStateOf<Route>(Route.Overview)
        private set
    var windowActive by mutableStateOf(true)
    var confirm by mutableStateOf<ConfirmRequest?>(null)
    var sessionUsage by mutableStateOf<Map<String, SessionUsage>>(emptyMap())
        private set
    var notificationsOpen by mutableStateOf(false)
    var usageOpen by mutableStateOf(false)
    var modelPickerOpen by mutableStateOf(false)
    var loginBannerDismissed by mutableStateOf(false)
    var taskDialogProjectId by mutableStateOf<Long?>(null)
    var taskDialogTemplateId by mutableStateOf<Long?>(null)
    var quickStartProjectId by mutableStateOf<Long?>(null)
    /** Project and live session whose queue receives the card chosen in the picker. */
    var queuePickTarget by mutableStateOf<Pair<Long, String>?>(null)
    var projectSettings by mutableStateOf<ProjectSettingsState?>(null)
        private set

    val views = mutableStateMapOf<Long, ProjectView>()
    val backgroundAgents = mutableStateMapOf<Long, List<BackgroundAgent>>()

    val projects: ProjectsState = ProjectsState(
        backend, scope,
        hasRunningSessions = { live.of(it).isNotEmpty() },
        sessionIdsOf = { id -> (viewOf(id).openSessions + viewOf(id).openShells + live.of(id).map { it.ccmSessionId }).distinct() },
        closeSession = { projectId, id -> if (shells.owns(id)) shells.close(projectId, id) else sessions.close(projectId, id) },
        requestConfirm = { confirm = it },
        showProject = { navigate(Route.Project(it)) },
        onRemoved = { if (route == Route.Project(it)) route = Route.Overview },
        onLoaded = ::forgetRemovedProjects,
        reportError = { title, message -> notifications.reportError(title, message) },
        reportActionError = { notifications.reportError(it) },
    )
    val notifications: NotificationsState = NotificationsState({ projects.nameOf(it) }) { windowActive }
    val sessions = SessionsState(
        backend, scope, { live }, { sessionUsage[it] }, { teams.defaultTeamOf(it) },
        onOpened = { projectId, id -> views[projectId] = viewOf(projectId).openSession(id) },
        onClosed = { projectId, id -> views[projectId] = viewOf(projectId).closeSession(id) },
        showProject = { navigate(Route.Project(it)) },
        requestConfirm = { confirm = it },
        reportError = notifications::reportError,
    )
    val queue = QueueState(backend.sessions, backend.terminal, scope, sessions::isAttached, sessions::sendQueued)
    val runs = RunState(
        backend.runConfigurations, backend.runs, backend.terminal, scope,
        projectPath = { projects.find(it)?.path },
        showRunTab = { select(it, ProjectTab.Run) },
        requestConfirm = { confirm = it },
        reportError = notifications::reportError,
    )
    val shells = ShellsState(
        backend, scope,
        openShellsOf = { viewOf(it).openShells },
        lastChosen = { settings.lastShell },
        onChosen = { name -> updateSettings { it.copy(lastShell = name) } },
        onOpened = { projectId, id -> views[projectId] = viewOf(projectId).openShell(id); navigate(Route.Project(projectId)) },
        onClosed = { projectId, id -> views[projectId]?.let { views[projectId] = it.closeShell(id) } },
        reportError = notifications::reportError,
    )
    val proposals = ProposalState(backend.proposals, scope, notifications::reportError) { terminal.focus(it) }
    val teams = TeamsState(backend.teams, scope, { confirm = it }, notifications::reportError)
    val prompts = PromptsState(backend.promptTemplates, scope, { confirm = it }, notifications::reportError, listOf(backend.projects::subscribe, backend.teams::subscribe))
    val plugins = PluginsState(backend.plugins, scope, { confirm = it }, notifications::toast)
    val mcp = McpState(backend.mcp, null, scope, { confirm = it }, notifications::toast)
    private val skillHost = SkillHost(::openDirectory, ::openInIde) { ideAvailable }
    val skills = SkillsState(backend.skills, null, scope, { confirm = it }, notifications::toast, skillHost)
    val author = AuthorProfileState(
        backend.author, scope,
        sessionProject = { (route as? Route.Project)?.let { projects.find(it.id) } ?: projects.all.firstOrNull() },
        projectOf = projects::find,
        launchSetup = sessions::launchSetup,
        mcpOf = { path -> if (path == null) mcp else McpState(backend.mcp, path, scope, { confirm = it }, notifications::toast) },
        reportError = notifications::reportError,
        toast = notifications::toast,
    )
    val update = UpdateState(backend.updates, scope, notifications::reportError, ::requestQuit) { exitApp() }
    var exitApp: () -> Unit = {}
    val agents = AgentsState(backend.transcripts, scope, { live }, ::showSessionTerminal)
    private val unsubscribers = mutableListOf<Runnable>()
    private val gitStates = mutableMapOf<Long, GitState>()
    private val gitHost = GitHost({ settings.git }, { change -> updateSettings { it.copy(git = change(it.git)) } }, notifications::toast)
    private val projectReload = Refresher(scope) { guarded { projects.load() } }
    private val sessionReload = Refresher(scope) { guarded { loadSessions() } }

    fun viewOf(projectId: Long): ProjectView = views[projectId] ?: ProjectView()

    /** The proposal dialog to show: the oldest unhidden proposal of the session whose terminal tab is open. */
    val proposalDialog: Proposal?
        get() {
            val project = route as? Route.Project ?: return null
            val session = viewOf(project.id).selected as? ProjectTab.Session ?: return null
            return proposals.dialogFor(session.ccmSessionId)
        }

    /** Answers the next session waiting for the user after the focused one; false when none waits. */
    fun answerNextWaiting(): Boolean {
        val focused = (route as? Route.Project)?.let { viewOf(it.id).focusedSession }
        val next = nextWaiting(live.sessions, focused)
        if (next == null) {
            notifications.toast(NotificationKind.INFO, "No session is waiting", "Nothing needs your answer right now.")
            return false
        }
        answerProposals(next.ccmSessionId)
        return true
    }

    /** Opens the session and shows its proposals again, also those the user hid before. */
    fun answerProposals(ccmSessionId: String) {
        proposals.reopen(ccmSessionId)
        openSession(ccmSessionId)
    }

    fun start() {
        unsubscribers += backend.projects.subscribe { projectReload.request() }
        unsubscribers += backend.sessions.subscribe { sessionReload.request() }
        unsubscribers += backend.rateLimits.subscribe { scope.launch { rateLimits = backend.rateLimits.current().orElse(null) } }
        unsubscribers += backend.sessionUsage.subscribe { scope.launch { sessionUsage = backend.sessionUsage.all() } }
        unsubscribers += backend.notifications.subscribe { notification -> scope.launch { notifications.receive(notification) } }
        backend.sound?.setEnabled(settings.notificationSound)
        teams.start()
        prompts.start()
        queue.start()
        author.offerAtStart()
        runs.start()
        shells.detect()
        proposals.start()
        update.start()
        projectReload.request()
        sessionReload.request()
        scope.launch { rateLimits = backend.rateLimits.current().orElse(null) }
        scope.launch { sessionUsage = backend.sessionUsage.all() }
        scope.launch { guarded { notifications.restore(io { backend.notifications.history() }) } }
        scope.launch { guarded { installation = io { backend.installation.current() } } }
        scope.launch { guarded { ideAvailable = io { backend.ide.available() } } }
        scope.launch {
            var ticks = 0
            while (true) {
                delay(TICK_MILLIS)
                now = Instant.now()
                if (++ticks % STALE_REFRESH_TICKS == 0) sessionReload.request()
            }
        }
    }

    fun dispose() {
        cancelProjectSettings()
        teams.dispose()
        prompts.dispose()
        queue.dispose()
        runs.dispose()
        proposals.dispose()
        unsubscribers.forEach { it.run() }
        unsubscribers.clear()
    }

    fun recheckInstallation() = scope.launch {
        checkingInstallation = true
        guarded { installation = io { backend.installation.recheck() } }
        checkingInstallation = false
    }

    /** Opens the settings dialog of [project] with its own extension states, so the global pages stay untouched. */
    fun openProjectSettings(project: Project, section: ProjectSettingsSection = ProjectSettingsSection.PLUGINS) {
        cancelProjectSettings()
        val dialogScope = CoroutineScope(scope.coroutineContext + SupervisorJob(scope.coroutineContext[Job]))
        projectSettings = ProjectSettingsState(
            ExtensionContext.ForProject(project.id, project.name, project.path),
            section,
            dialogScope,
            PluginsState(backend.plugins, dialogScope, { confirm = it }, notifications::toast),
            McpState(backend.mcp, project.path, dialogScope, { confirm = it }, notifications::toast),
            SkillsState(backend.skills, project.path, dialogScope, { confirm = it }, notifications::toast, skillHost),
        )
    }

    private fun cancelProjectSettings() {
        projectSettings?.dispose()
        projectSettings = null
    }

    fun closeProjectSettings() {
        val dialog = projectSettings ?: return
        if (dialog.busy) {
            notifications.toast(NotificationKind.INFO, "Wait for the running action to finish", "Project settings can be closed afterwards.")
            return
        }
        cancelProjectSettings()
        reloadGlobalExtensions()
    }

    /** The dialog may have changed what the global pages list; reload only pages already shown, without the catalogue or a health check. */
    private fun reloadGlobalExtensions() {
        if (plugins.loaded) plugins.refresh(reloadCatalog = false)
        if (mcp.loaded) mcp.refresh(withHealth = mcp.healthCheck == HealthCheck.DONE)
        if (skills.loaded) skills.refresh()
    }

    fun navigate(target: Route) {
        route = target
        (target as? Route.Project)?.let { author.offerForProject(it.id) }
        notificationsOpen = false
        usageOpen = false
        modelPickerOpen = false
    }

    fun openSession(ccmSessionId: String) {
        val session = live.find(ccmSessionId) ?: return
        views[session.projectId] = viewOf(session.projectId).openSession(ccmSessionId)
        navigate(Route.Project(session.projectId))
    }

    fun select(projectId: Long, tab: ProjectTab) {
        views[projectId] = viewOf(projectId).select(tab)
    }

    /** Opens the project's Git tab, on [view] when given, and returns its state. */
    fun openGit(projectId: Long, view: GitSubView? = null): GitState? {
        select(projectId, ProjectTab.Git)
        return gitOf(projectId)?.also { git -> view?.let(git::show) }
    }

    fun openNotification(target: AppNotification) {
        val session = target.ccmSessionId?.let { live.find(it) }
        if (session != null) openSession(session.ccmSessionId) else navigate(Route.Project(target.projectId))
    }

    fun openToast(toast: ToastItem) {
        notifications.dismissToast(toast.id)
        val session = toast.ccmSessionId?.let { live.find(it) }
        when {
            session != null -> openSession(session.ccmSessionId)
            toast.projectId != null -> navigate(Route.Project(toast.projectId))
        }
    }

    /** Opens the folder picker; the chosen directory is handed to [ProjectsState.addAt]. */
    fun requestAddProject() {
        paletteOpen = false
        projects.pickerOpen = true
    }

    fun updateSettings(transform: (UiSettings) -> UiSettings) {
        settings = transform(settings)
        settingsStore.save(settings)
        backend.sound?.setEnabled(settings.notificationSound)
    }

    fun toggleSidebar() = updateSettings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }
    fun setGroupSessionsByProject(group: Boolean) = updateSettings { it.copy(groupSessionsByProject = group) }

    fun toggleNotificationSound() = updateSettings { it.copy(notificationSound = !it.notificationSound) }

    fun toggleAgents() = updateSettings { it.copy(agentsOpen = !it.agentsOpen) }

    fun toggleQuickBar() = updateSettings { it.copy(quickBarOpen = !it.quickBarOpen) }

    fun setThemeMode(mode: ThemeMode) = updateSettings { it.copy(themeMode = mode) }

    /** Flips between light and dark, starting from whatever [systemDark] says when following the system. */
    fun toggleTheme(systemDark: Boolean) {
        val dark = when (settings.themeMode) {
            ThemeMode.SYSTEM -> systemDark
            ThemeMode.DARK -> true
            ThemeMode.LIGHT -> false
        }
        setThemeMode(if (dark) ThemeMode.LIGHT else ThemeMode.DARK)
    }

    fun openFolder(project: Project) = openDirectory(project.path)

    fun openDirectory(directory: Path) = scope.launch { guarded { io { backend.directories.open(directory) } } }

    fun openIde(project: Project) = openInIde(project.path)

    fun openInIde(target: Path) = scope.launch { guarded { io { backend.ide.open(target) } } }

    fun openTaskDialog(projectId: Long, templateId: Long? = null) {
        taskDialogTemplateId = templateId
        taskDialogProjectId = projectId
    }

    fun closeTaskDialog() {
        taskDialogProjectId = null
        taskDialogTemplateId = null
    }

    /** Branch of the project's repository when its git state is already loaded. */
    fun branchOf(projectId: Long): String? = gitOf(projectId)?.status?.branch?.takeIf { it.isNotBlank() }

    fun placeholderValuesOf(project: Project) = PlaceholderValues(project.name, branchOf(project.id), null)

    fun queueFromTemplate(project: Project, ccmSessionId: String, template: PromptTemplate) {
        queuePickTarget = null
        queue.add(ccmSessionId, PlaceholderSubstitution.apply(template.text, placeholderValuesOf(project)))
    }

    /** Starts [template] at once in [project], led by its default team or else the project's default team. */
    fun startTaskFromTemplate(project: Project, template: PromptTemplate) {
        quickStartProjectId = null
        val team = teamForTemplate(template, teams.teams, teams.defaultTeamOf(project.id))
        startTask(project, team, PlaceholderSubstitution.apply(template.text, placeholderValuesOf(project)).trim(), false)
    }

    /** Starts a session with [task] as its first prompt, led by [team] or without a team, and opens its terminal. */
    fun startTask(project: Project, team: Team?, task: String, makeDefault: Boolean) {
        sessions.launchTask(project.id, team, task) {
            closeTaskDialog()
            if (makeDefault && team != null) teams.setProjectDefault(project.id, team.id)
        }
    }

    private fun showSessionTerminal(ccmSessionId: String) {
        openSession(ccmSessionId)
        backend.terminal.focus(ccmSessionId)
    }

    /** Git state of a project, created on first use; null for an unknown project. */
    fun gitOf(projectId: Long): GitState? {
        gitStates[projectId]?.let { return it }
        val project = projects.find(projectId) ?: return null
        return GitState(projectId, project.path, backend.git, scope, gitHost).also { gitStates[projectId] = it }
    }

    fun gitSummaryOf(projectId: Long): GitSummary? = gitStates[projectId]?.summary

    fun refreshBackgroundAgents(projectId: Long) {
        val path: Path = projects.find(projectId)?.path ?: return
        scope.launch { guarded { backgroundAgents[projectId] = io { backend.backgroundAgents.list(path) } } }
    }

    val terminal: TerminalPort get() = backend.terminal

    val dataDir: Path get() = backend.dataDir

    suspend fun activityOf(ccmSessionId: String): List<ActivityEntry> =
        try {
            io { backend.hooks.recent(ccmSessionId, ACTIVITY_LIMIT) }
        } catch (e: RuntimeException) {
            emptyList()
        }

    fun requestQuit(quit: () -> Unit) {
        val running = live.sessions.size
        if (running == 0) return quit()
        val noun = if (running == 1) "Claude session is" else "Claude sessions are"
        confirm = ConfirmRequest(
            title = "Quit Claude Code Manager",
            message = "$running $noun running and will be terminated.",
            confirmLabel = "Quit",
            onConfirm = quit,
        )
    }

    private fun forgetRemovedProjects(projectIds: Set<Long>) {
        views.keys.retainAll(projectIds)
        gitStates.keys.filter { it !in projectIds }.forEach { gitStates.remove(it)?.close() }
        backgroundAgents.keys.retainAll(projectIds)
        runs.retainProjects(projectIds)
        (route as? Route.Project)?.takeIf { it.id !in projectIds }?.let { route = Route.Overview }
        sessionReload.request()
    }

    private suspend fun loadSessions() {
        val (sessions, statuses) = io {
            backend.sessions.sessions() to projects.all.associate { it.id to backend.sessions.projectStatus(it.id) }
        }
        val previous = live.sessions.associate { it.ccmSessionId to it.status }
        live = LiveSessions(sessions, statuses)
        agents.prune(sessions.map { it.ccmSessionId })
        notifications.dismissResolvedAttention(previous, sessions)
        projectsBecameIdle(previous, sessions).forEach { gitStates[it]?.refresh() }
    }

    private suspend fun guarded(onError: (String) -> Unit = notifications::reportError, block: suspend () -> Unit) = runGuarded(onError, block)
}
