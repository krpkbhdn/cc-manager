package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgent
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.ModelSelection
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsState
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val NOTIFICATION_CAP = 100
private const val TICK_MILLIS = 1_000L
private const val APPLY_TIMEOUT_MILLIS = 6_000L
private const val APPLY_POLL_MILLIS = 200L
private const val ACTIVITY_LIMIT = 300
private const val UNEXPECTED_ERROR = "Something went wrong"
private const val ACTION_FAILED_TITLE = "Action failed"
private const val ADD_FAILED_TITLE = "Could not add project"

/**
 * Single subscription point to the backend: re-reads snapshots on change callbacks (any thread) and exposes
 * Compose state. All state writes happen on [scope], which must run on the UI dispatcher.
 */
class AppState(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val settingsStore: UiSettingsStore = UiSettingsStore.inUserHome(),
) {
    var settings by mutableStateOf(settingsStore.load())
        private set
    var paletteOpen by mutableStateOf(false)
    var pickerOpen by mutableStateOf(false)
    var shortcutsOpen by mutableStateOf(false)

    var installation by mutableStateOf<ClaudeInstallation?>(null)
        private set
    var checkingInstallation by mutableStateOf(false)
        private set
    var ideAvailable by mutableStateOf(false)
        private set
    var projects by mutableStateOf<List<Project>>(emptyList())
        private set
    var rateLimits by mutableStateOf<RateLimitSnapshot?>(null)
        private set
    var live by mutableStateOf(LiveSessions())
        private set
    var notifications by mutableStateOf<List<AppNotification>>(emptyList())
        private set
    var readUpTo by mutableLongStateOf(0L)
        private set
    var now by mutableStateOf(Instant.now())
        private set
    var route by mutableStateOf<Route>(Route.Overview)
        private set
    var toasts by mutableStateOf<List<ToastItem>>(emptyList())
        private set
    var confirm by mutableStateOf<ConfirmRequest?>(null)
    var renaming by mutableStateOf<RenameRequest?>(null)
    var launching by mutableStateOf(false)
        private set
    var sessionUsage by mutableStateOf<Map<String, SessionUsage>>(emptyMap())
        private set
    var notificationsOpen by mutableStateOf(false)
    var usageOpen by mutableStateOf(false)
    var modelPickerOpen by mutableStateOf(false)
    var modelApplying by mutableStateOf(false)
        private set
    var loginBannerDismissed by mutableStateOf(false)
    var taskDialogProjectId by mutableStateOf<Long?>(null)

    val views = mutableStateMapOf<Long, ProjectView>()
    val sessionTeams = mutableStateMapOf<String, Team>()
    val backgroundAgents = mutableStateMapOf<Long, List<BackgroundAgent>>()
    val notificationEvents: SharedFlow<AppNotification> get() = events

    private val events = MutableSharedFlow<AppNotification>(extraBufferCapacity = NOTIFICATION_CAP)
    val teams = TeamsState(backend.teams, scope, { confirm = it }, { reportError(it) })
    private val commandWriter = SessionCommandWriter(backend.terminal)
    private val unsubscribers = mutableListOf<Runnable>()
    private val gitStates = mutableMapOf<Long, GitState>()
    private val gitHost = GitHost({ settings.git }, { change -> updateSettings { it.copy(git = change(it.git)) } }, ::toast)
    private var toastCounter = 0L
    private val projectReload = Refresher(scope) { guarded { loadProjects() } }
    private val sessionReload = Refresher(scope) { guarded { loadSessions() } }

    val unreadCount: Int get() = notifications.count { it.id > readUpTo }

    fun projectName(id: Long): String = projects.firstOrNull { it.id == id }?.name ?: "Unknown project"

    fun viewOf(projectId: Long): ProjectView = views[projectId] ?: ProjectView()

    fun start() {
        unsubscribers += backend.projects.subscribe { projectReload.request() }
        unsubscribers += backend.sessions.subscribe { sessionReload.request() }
        unsubscribers += backend.rateLimits.subscribe { scope.launch { rateLimits = backend.rateLimits.current().orElse(null) } }
        unsubscribers += backend.sessionUsage.subscribe { scope.launch { sessionUsage = backend.sessionUsage.all() } }
        unsubscribers += backend.notifications.subscribe { notification -> scope.launch { receive(notification) } }
        teams.start()
        projectReload.request()
        sessionReload.request()
        scope.launch { rateLimits = backend.rateLimits.current().orElse(null) }
        scope.launch { sessionUsage = backend.sessionUsage.all() }
        scope.launch { guarded { notifications = io { backend.notifications.history() }.takeLast(NOTIFICATION_CAP) } }
        scope.launch { guarded { installation = io { backend.installation.current() } } }
        scope.launch { guarded { ideAvailable = io { backend.ide.available() } } }
        scope.launch { while (true) { delay(TICK_MILLIS); now = Instant.now() } }
    }

    fun dispose() {
        teams.dispose()
        unsubscribers.forEach { it.run() }
        unsubscribers.clear()
    }

    fun recheckInstallation() = scope.launch {
        checkingInstallation = true
        guarded { installation = io { backend.installation.recheck() } }
        checkingInstallation = false
    }

    fun navigate(target: Route) {
        route = target
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
        dismissToast(toast.id)
        val session = toast.ccmSessionId?.let { live.find(it) }
        when {
            session != null -> openSession(session.ccmSessionId)
            toast.projectId != null -> navigate(Route.Project(toast.projectId))
        }
    }

    fun dismissToast(id: Long) {
        toasts = toasts.filterNot { it.id == id }
    }

    fun markAllRead() {
        readUpTo = notifications.maxOfOrNull { it.id } ?: readUpTo
    }

    /** Opens the folder picker; the chosen directory is handed to [addProjectAt]. */
    fun requestAddProject() {
        paletteOpen = false
        pickerOpen = true
    }

    fun addProjectAt(directory: Path) {
        pickerOpen = false
        scope.launch {
            guarded(onError = { reportError(ADD_FAILED_TITLE, it) }) {
                val added = io { backend.projects.add(directory) }
                loadProjects()
                navigate(Route.Project(added.id))
            }
        }
    }

    fun updateSettings(transform: (UiSettings) -> UiSettings) {
        settings = transform(settings)
        settingsStore.save(settings)
    }

    fun toggleSidebar() = updateSettings { it.copy(sidebarCollapsed = !it.sidebarCollapsed) }

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

    fun renameProject(project: Project, name: String) {
        renaming = null
        scope.launch { guarded { io { backend.projects.rename(project.id, name.trim()) }; loadProjects() } }
    }

    fun selectModel(project: Project, selection: ModelSelection) {
        scope.launch { guarded { io { backend.projects.selectModel(project.id, selection) }; loadProjects() } }
    }

    fun requestRemove(project: Project) {
        confirm = ConfirmRequest(
            title = "Remove project",
            message = "Remove \"${project.name}\" from the manager? Files on disk stay untouched.",
            confirmLabel = "Remove",
        ) { removeProject(project) }
    }

    private fun removeProject(project: Project) = scope.launch {
        guarded {
            io { backend.projects.remove(project.id) }
            if (route == Route.Project(project.id)) route = Route.Overview
            loadProjects()
        }
    }

    fun openFolder(project: Project) = openDirectory(project.path)

    fun openDirectory(directory: Path) = scope.launch { guarded { io { backend.directories.open(directory) } } }

    fun openIde(project: Project) = openInIde(project.path)

    fun openInIde(target: Path) = scope.launch { guarded { io { backend.ide.open(target) } } }

    fun launch(projectId: Long, mode: LaunchMode) = launchSession(projectId) { backend.launcher.launch(projectId, mode) }

    /** Starts a lead session for [team] with [task] as its first prompt and opens its terminal. */
    fun startTask(project: Project, team: Team, task: String, makeDefault: Boolean) {
        taskDialogProjectId = null
        if (makeDefault) teams.setProjectDefault(project.id, team.id)
        launchSession(project.id, team) { backend.launcher.launchTeam(project.id, team, task) }
    }

    private fun launchSession(projectId: Long, team: Team? = null, start: () -> LaunchedSession) {
        if (launching) return
        launching = true
        scope.launch {
            guarded {
                val session = io(start)
                team?.let { sessionTeams[session.ccmSessionId()] = it }
                backend.terminal.open(session)
                views[projectId] = viewOf(projectId).openSession(session.ccmSessionId)
                navigate(Route.Project(projectId))
            }
            launching = false
        }
    }

    /**
     * Types [command] into the running [session] and shows "applying" until the statusLine reports [reached] or a few seconds
     * pass; claude refreshes the statusLine on its own after /model and /effort, but not always visibly different.
     */
    fun applyToSession(session: SessionSnapshot, command: String, reached: (SessionUsage?) -> Boolean) {
        if ((modelTarget(session, modelApplying) as? ModelTarget.Running)?.blockedReason != null) return
        modelApplying = true
        scope.launch {
            guarded {
                commandWriter.run(session.ccmSessionId, command)
                var waited = 0L
                while (!reached(sessionUsage[session.ccmSessionId]) && waited < APPLY_TIMEOUT_MILLIS) {
                    delay(APPLY_POLL_MILLIS)
                    waited += APPLY_POLL_MILLIS
                }
            }
            modelApplying = false
        }
    }

    /** Opens Claude's own model switcher (Alt+P) inside the session's terminal and moves the keyboard there. */
    fun openClaudeModelPicker(session: SessionSnapshot) {
        if ((modelTarget(session, modelApplying) as? ModelTarget.Running)?.blockedReason != null) return
        commandWriter.openModelPicker(session.ccmSessionId)
        backend.terminal.focus(session.ccmSessionId)
    }

    fun attach(projectId: Long, backgroundId: String) {
        scope.launch {
            guarded {
                val session = io { backend.launcher.attach(projectId, backgroundId) }
                backend.terminal.open(session)
                views[projectId] = viewOf(projectId).openSession(session.ccmSessionId)
            }
        }
    }

    fun requestCloseSession(projectId: Long, ccmSessionId: String) {
        if (live.find(ccmSessionId) == null) return closeSession(projectId, ccmSessionId)
        confirm = ConfirmRequest(
            title = "Close session",
            message = "The Claude process of this session is still running and will be terminated.",
            confirmLabel = "Terminate",
        ) { closeSession(projectId, ccmSessionId) }
    }

    private fun closeSession(projectId: Long, ccmSessionId: String) {
        views[projectId] = viewOf(projectId).closeSession(ccmSessionId)
        scope.launch { guarded { io { backend.terminal.close(ccmSessionId) } } }
    }

    fun send(ccmSessionId: String, keys: String) {
        scope.launch { guarded { backend.terminal.send(ccmSessionId, keys) } }
    }

    /** Git state of a project, created on first use; null for an unknown project. */
    fun gitOf(projectId: Long): GitState? {
        gitStates[projectId]?.let { return it }
        val project = projects.firstOrNull { it.id == projectId } ?: return null
        return GitState(projectId, project.path, backend.git, scope, gitHost).also { gitStates[projectId] = it }
    }

    fun toast(kind: NotificationKind, title: String, body: String) = pushToast(ToastItem(nextToastId(), kind, title, body))

    fun gitSummaryOf(projectId: Long): GitSummary? = gitStates[projectId]?.summary

    fun refreshBackgroundAgents(projectId: Long) {
        val path: Path = projects.firstOrNull { it.id == projectId }?.path ?: return
        scope.launch { guarded { backgroundAgents[projectId] = io { backend.backgroundAgents.list(path) } } }
    }

    val terminal: TerminalPort get() = backend.terminal

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

    fun reportError(message: String) = reportError(ACTION_FAILED_TITLE, message)

    fun reportError(title: String, message: String) = pushToast(ToastItem(nextToastId(), NotificationKind.ERROR, title, message))

    private fun nextToastId() = -(++toastCounter)

    private fun pushToast(toast: ToastItem) {
        toasts = (toasts + toast).takeLast(MAX_TOASTS)
    }

    private suspend fun receive(notification: AppNotification) {
        if (notifications.any { it.id == notification.id }) return
        notifications = (notifications + notification).takeLast(NOTIFICATION_CAP)
        pushToast(
            ToastItem(
                id = notification.id,
                kind = notification.kind,
                title = "${projectName(notification.projectId)}: ${notification.title}",
                body = notification.body,
                projectId = notification.projectId,
                ccmSessionId = notification.ccmSessionId,
            ),
        )
        events.emit(notification)
    }

    private suspend fun loadProjects() {
        val loaded = io { backend.projects.list() }
        projects = loaded
        views.keys.retainAll(loaded.map { it.id }.toSet())
        gitStates.keys.retainAll(loaded.map { it.id }.toSet())
        (route as? Route.Project)?.takeIf { current -> loaded.none { it.id == current.id } }?.let { route = Route.Overview }
        sessionReload.request()
    }

    private suspend fun loadSessions() {
        val (sessions, statuses) = io {
            backend.sessions.sessions() to projects.associate { it.id to backend.sessions.projectStatus(it.id) }
        }
        val previous = live.sessions.associate { it.ccmSessionId to it.status }
        live = LiveSessions(sessions, statuses)
        projectsBecameIdle(previous, sessions).forEach { gitStates[it]?.refresh() }
    }

    /** A toast asking for the user is stale once its session left NEEDS_ACTION, whoever answered. */
    private fun dismissResolvedAttentionToasts(previous: Map<String, AgentStatus>, current: List<SessionSnapshot>) {
        val resolved = current.filter { previous[it.ccmSessionId] == AgentStatus.NEEDS_ACTION && it.status != AgentStatus.NEEDS_ACTION }.map { it.ccmSessionId }.toSet()
        if (resolved.isEmpty()) return
        toasts = toasts.filterNot { it.kind == NotificationKind.NEEDS_ACTION && it.ccmSessionId in resolved }
    }

    private suspend fun <T> io(block: () -> T): T = withContext(Dispatchers.IO) { block() }

    private suspend fun guarded(onError: (String) -> Unit = ::reportError, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            onError(e.message ?: UNEXPECTED_ERROR)
        }
    }

    private companion object {
        const val MAX_TOASTS = 4
    }
}
