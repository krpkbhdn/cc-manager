package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.install.LoginState
import com.github.krpkbhdn.ccmanager.claude.launch.BackgroundAgent
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartupState
import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantState
import com.github.krpkbhdn.ccmanager.ui.assistant.ToolSummary
import com.github.krpkbhdn.ccmanager.ui.author.AuthorProfileState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.history.SessionSearchState
import com.github.krpkbhdn.ccmanager.ui.review.ReviewState
import com.github.krpkbhdn.ccmanager.ui.sessions.SessionsTab
import com.github.krpkbhdn.ccmanager.ui.prompts.PromptsState
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.ui.proposal.ProposalState
import com.github.krpkbhdn.ccmanager.ui.proposal.proposalHeadline
import com.github.krpkbhdn.ccmanager.ui.queue.QueueState
import com.github.krpkbhdn.ccmanager.ui.queue.QueuedPrompt
import com.github.krpkbhdn.ccmanager.ui.run.RunState
import com.github.krpkbhdn.ccmanager.ui.settings.SettingsState
import com.github.krpkbhdn.ccmanager.ui.presets.PresetsState
import com.github.krpkbhdn.ccmanager.ui.presets.apply.ApplyPresetState
import com.github.krpkbhdn.ccmanager.ui.presets.apply.PresetOfferState
import com.github.krpkbhdn.ccmanager.ui.teams.TeamsState
import com.github.krpkbhdn.ccmanager.ui.usage.UsageState
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceSettingsState
import com.github.krpkbhdn.ccmanager.ui.voice.voiceSettingsOf
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(AppState::class.java)
private const val TICK_MILLIS = 1_000L
private const val STALE_REFRESH_TICKS = 30
private const val UNOPENED_GIT_REFRESH_TICKS = 60
private const val ACTIVITY_LIMIT = 300
private const val COMPACT_COMMAND = "/compact"

/**
 * Single subscription point to the backend: re-reads snapshots on change callbacks (any thread) and exposes
 * Compose state. All state writes happen on [scope], which must run on the UI dispatcher.
 */
class AppState(
    private val backend: Backend,
    private val scope: CoroutineScope,
    settingsStore: UiSettingsStore = UiSettingsStore.inDataDir(backend.dataDir),
) {
    val settings: UiSettings get() = prefs.settings
    var paletteOpen by mutableStateOf(false)
    var shortcutsOpen by mutableStateOf(false)

    var live by mutableStateOf(LiveSessions())
        private set
    val workingStaleAfter: Duration = backend.sessionProperties.workingStaleAfter()
    var now by mutableStateOf(Instant.now())
        private set
    var route by mutableStateOf<Route>(Route.Overview)
        private set
    var sessionsTab by mutableStateOf(SessionsTab.ACTIVE)
        private set
    var searchFocusRequests by mutableIntStateOf(0)
        private set
    var windowActive by mutableStateOf(true)
    var confirm by mutableStateOf<ConfirmRequest?>(null)
    var notificationsOpen by mutableStateOf(false)
    var usageOpen by mutableStateOf(false)
    var contextSessionId by mutableStateOf<String?>(null)
    var contextShowDetails by mutableStateOf(false)
    var modelPickerOpen by mutableStateOf(false)
    var quickRequestOpen by mutableStateOf(false)
    var assistantHelpOpen by mutableStateOf(false)
    val assistantTools: List<ToolSummary> = backend.assistantTools.map { ToolSummary(it.name(), it.description()) }
    var loginBannerDismissed by mutableStateOf(false)

    val views = mutableStateMapOf<Long, ProjectView>()
    val backgroundAgents = mutableStateMapOf<Long, List<BackgroundAgent>>()

    val prefs = UiPreferences(settingsStore, scope, backend.sound, backend.usageAlerts, backend.usageDefaults) { notifications.reportError(it) }
    val environment = ClaudeEnvironment(backend, scope) { notifications.reportError(it) }
    val projects: ProjectsState = ProjectsState(
        backend, scope,
        hasRunningSessions = { live.of(it).isNotEmpty() },
        sessionIdsOf = { id -> (viewOf(id).openSessions + viewOf(id).openShells + live.of(id).map { it.ccmSessionId }).distinct() },
        closeSession = { projectId, id -> if (shells.owns(id)) shells.close(projectId, id) else sessions.close(projectId, id) },
        requestConfirm = { confirm = it },
        showProject = { navigate(Route.Project(it)) },
        onRemoved = { if (route == Route.Project(it)) route = Route.Overview },
        onLoaded = { forgetRemovedProjects(it); gits.loadNew(it); prefs.retainFoldedGroups(it) },
        reportError = { title, message -> notifications.reportError(title, message) },
        reportActionError = { notifications.reportError(it) },
    )
    val notifications: NotificationsState = NotificationsState({ projects.nameOf(it) }) { windowActive }
    val sessions = SessionsState(
        backend, scope, { live }, { environment.sessionUsage[it] }, { teams.defaultTeamOf(it) },
        onOpened = ::showSessionTab,
        onClosed = { projectId, id -> views[projectId] = viewOf(projectId).closeSession(id) },
        showProject = { navigate(routeOf(it)) },
        requestConfirm = { confirm = it },
        reportError = notifications::reportError,
        projectPath = { projects.find(it)?.path },
    )
    val contextBreakdown = ContextBreakdownState({ target, force -> backend.contextBreakdown.measure(target.directory, target.claudeSessionId, force) }, scope, ::contextTargetOf)
    val contextAlerts = ContextAlerts()
    val assistant = AssistantState(
        scope, backend.terminal, { live }, backend.dataDir.resolve("assistant"),
        launch = { sessions.launchAssistant(it) }, show = { navigate(Route.Assistant) }, enqueue = { id, text -> queue.add(id, text) }, requestConfirm = { confirm = it },
        reportError = notifications::reportError,
    )
    val assistantStartup = AssistantStartupState(
        scope, { settings.assistantStartup }, { mode -> prefs.update { it.copy(assistantStartup = mode) } },
        blocked = { author.dialogOpen || author.startOfferPending || confirm != null || applyPreset.isOpen || presetOffer.offer != null },
        startInBackground = assistant::startInBackground, start = assistant::start,
    )
    val queue = QueueState(backend.sessions, backend.terminal, scope, sessions::isAttached, sessions::sendQueued)
    val usage = UsageState(backend.usageHistory, backend.sessionUsage, scope, visible = { route == Route.Usage }, reportError = notifications::reportError)
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
        onChosen = { name -> prefs.update { it.copy(lastShell = name) } },
        onOpened = { projectId, id -> views[projectId] = viewOf(projectId).openShell(id); navigate(Route.Project(projectId)) },
        onClosed = { projectId, id -> views[projectId]?.let { views[projectId] = it.closeShell(id) } },
        reportError = notifications::reportError,
    )
    val proposals = ProposalState(backend.proposals, scope, notifications::reportError, focusTerminal = { terminal.focus(it) }, onArrived = ::announceAssistantProposal)
    val answers = ProposalAnswers(proposals, assistant, { route }, ::viewOf, { live }, notifications::toast, ::openSession)
    val osNotifications = OsNotifications(
        scope, { settings }, { windowActive },
        isWaiting = { live.find(it)?.status == AgentStatus.NEEDS_ACTION },
        proposalOf = { proposals.of(it).firstOrNull() },
        projectName = { projects.nameOf(it) },
        show = notifications::popup,
    )
    var flashTaskbar: (NotificationKind) -> Unit = {}
    var trayAvailable by mutableStateOf(false)
    val waitingCount: Int get() = live.sessions.count { it.status == AgentStatus.NEEDS_ACTION }
    val teams = TeamsState(backend.teams, scope, { confirm = it }, notifications::reportError)
    val presets = PresetsState(backend.presets, backend.plugins, backend.skills, scope, { confirm = it }, notifications::reportError)
    val applyPreset: ApplyPresetState = ApplyPresetState(backend.presetApply, backend.presetApplications, { presets.presets }, projects::find, scope, notifications::reportError)
    val presetOffer: PresetOfferState = PresetOfferState(
        backend.presetApplications, { presets.presets }, projects::find, scope,
        blocked = ::dialogBlocksOffer,
        review = { projectId, presetId -> applyPreset.open(projectId, presetId) },
        reportError = notifications::reportError,
    )
    val prompts = PromptsState(backend.promptTemplates, scope, { confirm = it }, notifications::reportError, listOf(backend.projects::subscribe, backend.teams::subscribe))
    val sessionSearch = SessionSearchState(backend.sessionSearch, backend.transcripts, scope, { confirm = it }, notifications::reportError)
    val reviews = ReviewState(
        backend.review, { live }, { id, text -> queue.add(id, text) },
        { projectId, prompt, onNotStarted, onStarted -> sessions.launchTask(projectId, null, prompt, { onNotStarted() }, onStarted) },
        scope, notifications::reportError,
    )
    val extensions = ExtensionStates(backend, scope, { confirm = it }, notifications, environment)
    val author = AuthorProfileState(
        backend.author, scope,
        mcp = extensions.mcp,
        reportError = notifications::reportError,
        toast = notifications::toast,
    )
    val settingsDialog = SettingsState()
    val voice: VoiceSettingsState? = voiceSettingsOf(backend.voice, scope, { settings }, prefs::update, notifications::reportError)
    val update = UpdateState(backend.updates, scope, notifications::reportError, ::requestQuit) { exitApp() }
    var exitApp: () -> Unit = {}
    val agents = AgentsState(backend.transcripts, scope, { live }, ::showSessionTerminal)
    private val unsubscribers = mutableListOf<Runnable>()
    private val refreshingAgents = mutableSetOf<Long>()
    val worktrees: WorktreeActions = WorktreeActions(projects, sessions, shells, runs, backend.git.taskWorktrees, backend.terminal) { gits }
    val gits = ProjectGits(
        backend.git, scope,
        GitHost(
            { settings.git }, { change -> prefs.update { it.copy(git = change(it.git)) } }, notifications::toast,
            worktrees::showWorktree, worktrees::forget, worktrees::stopWork, worktrees::hasRunningWork, worktrees::workStopping, environment::openDirectory,
            backend.runConfigurations::deleteByDirectory, { dir -> backend.runConfigurations.listByDirectory(dir).map { it.name() } },
            worktrees::forgetStored,
        ),
        projects::find,
        backend.git.taskWorktrees::forProject,
        { sessions.sessionDirectories },
    )
    val tasks = TaskDialogState(sessions, teams, queue, scope, backend.git.taskWorktrees, notifications::reportError, gits::branchOf, { projectId, sessionId -> gits.branchOf(projectId, sessionId) }, gits::taskRowsChanged)
    private val projectReload = Refresher(scope) { guarded { projects.load() } }
    private val sessionReload = Refresher(scope) { guarded { loadSessions() } }

    fun updateSettings(transform: (UiSettings) -> UiSettings) = prefs.update(transform)

    fun viewOf(projectId: Long): ProjectView = views[projectId] ?: ProjectView()

    fun start() {
        unsubscribers += backend.projects.subscribe { projectReload.request() }
        unsubscribers += backend.sessions.subscribe { sessionReload.request() }
        unsubscribers += backend.notifications.subscribe { notification ->
            scope.launch {
                notifications.receive(notification)
                osNotifications.offer(notification)
                flashTaskbar(notification.kind)
            }
        }
        backend.appControl?.let { unsubscribers += it.connect(AppControlAdapter(this, scope, backend.appControlProperties.uiTimeout())) }
        scope.launch { snapshotFlow { environment.sessionUsage }.collect(contextAlerts::rearmBelowCritical) }
        teams.start()
        presets.start()
        prompts.start()
        sessionSearch.start()
        reviews.start()
        queue.start()
        author.offerAtStart()
        runs.start()
        usage.start()
        shells.detect()
        proposals.start()
        update.start()
        projectReload.request()
        sessionReload.request()
        unsubscribers += environment.start { installation ->
            assistantStartup.atStart(installation?.let { it.installed && it.login != LoginState.LOGGED_OUT } == true)
        }
        scope.launch { guarded { notifications.restore(io { backend.notifications.history() }) } }
        scope.launch {
            var ticks = 0
            var wasActive = windowActive
            while (true) {
                delay(TICK_MILLIS)
                now = Instant.now()
                if (++ticks % STALE_REFRESH_TICKS == 0) sessionReload.request()
                val active = windowActive
                val refocused = active && !wasActive
                wasActive = active
                if (active && (refocused || ticks % UNOPENED_GIT_REFRESH_TICKS == 0)) gits.reloadUnopened((route as? Route.Project)?.id, afterRunningSweep = refocused)
            }
        }
    }

    fun dispose() {
        extensions.dispose()
        teams.dispose()
        presets.dispose()
        prompts.dispose()
        sessionSearch.dispose()
        reviews.dispose()
        queue.dispose()
        voice?.let { Thread(it::close, "voice-close").apply { isDaemon = false }.start() }
        runs.dispose()
        usage.dispose()
        proposals.dispose()
        unsubscribers.forEach { it.run() }
        unsubscribers.clear()
    }

    private fun announceAssistantProposal(proposal: Proposal) {
        if (route != Route.Assistant && proposal.ccmSessionId() == assistant.liveSessionId) {
            notifications.needsAnswer(ClaudeLauncher.ASSISTANT_PROJECT_ID, proposal.ccmSessionId(), "Assistant: needs your answer", proposalHeadline(proposal))
        }
    }

    private fun contextTargetOf(ccmSessionId: String): ContextTarget? {
        val session = live.find(ccmSessionId) ?: return null
        val directory = sessions.sessionDirectories[ccmSessionId] ?: projects.find(session.projectId())?.path ?: return null
        return ContextTarget(directory, session.claudeSessionId())
    }

    /** /compact is a model call that rewrites the conversation, so it is confirmed first. */
    fun requestCompactContext(ccmSessionId: String) {
        confirm = ConfirmRequest("Compact this session?", "Types /compact into the session. Claude summarises the conversation, which uses the model.", "Compact", destructive = false) { compactContext(ccmSessionId) }
    }

    /** Types /compact into the session; refused with the usual reason while it is busy. */
    fun compactContext(ccmSessionId: String) {
        sessions.send(ccmSessionId, COMPACT_COMMAND + Keys.ENTER) { contextAlerts.dismiss(ccmSessionId) }
    }

    private fun dialogBlocksOffer(): Boolean = author.dialogOpen || author.startOfferPending || assistantStartup.asking || confirm != null || settingsDialog.open ||
        applyPreset.isOpen || presetOffer.offer != null || presets.dialog != null || answers.dialog != null || tasks.projectId != null

    fun navigate(target: Route) {
        route = target
        (target as? Route.Project)?.let { presetOffer.offerFor(it.id) }
        notificationsOpen = false
        usageOpen = false
        contextSessionId = null
        contextShowDetails = false
        modelPickerOpen = false
    }

    fun openSessions(tab: SessionsTab) {
        sessionsTab = tab
        if (tab == SessionsTab.HISTORY) searchFocusRequests++
        navigate(Route.Sessions)
    }

    fun selectSessionsTab(tab: SessionsTab) {
        sessionsTab = tab
    }

    fun openSession(ccmSessionId: String) {
        val session = live.find(ccmSessionId) ?: return
        showSessionTab(session.projectId, ccmSessionId)
        navigate(routeOf(session.projectId))
    }

    private fun showSessionTab(projectId: Long, ccmSessionId: String) {
        views[projectId] = viewOf(projectId).openSession(ccmSessionId)
    }

    private fun routeOf(projectId: Long): Route =
        if (projectId == ClaudeLauncher.ASSISTANT_PROJECT_ID) Route.Assistant else Route.Project(projectId)

    fun select(projectId: Long, tab: ProjectTab) {
        views[projectId] = viewOf(projectId).select(tab)
    }

    /** Opens the project's Git tab, on [view] when given, and returns its state. */
    fun openGit(projectId: Long, view: GitSubView? = null): GitState? {
        select(projectId, ProjectTab.Git)
        return gits.of(projectId)?.also { git -> view?.let(git::show) }
    }

    fun openNotification(target: AppNotification) {
        val session = target.ccmSessionId?.let { live.find(it) }
        when {
            target.kind == NotificationKind.USAGE_ALERT -> navigate(Route.Usage)
            session != null -> openSession(session.ccmSessionId)
            else -> navigate(routeOf(target.projectId))
        }
    }

    fun openToast(toast: ToastItem) {
        notifications.dismissToast(toast.id)
        val session = toast.ccmSessionId?.let { live.find(it) }
        when {
            session != null && toast.kind == NotificationKind.NEEDS_ACTION -> answers.answer(session.ccmSessionId)
            toast.kind == NotificationKind.USAGE_ALERT -> navigate(Route.Usage)
            session != null -> openSession(session.ccmSessionId)
            toast.projectId != null -> navigate(routeOf(toast.projectId))
            toast.summary && toast.kind == NotificationKind.NEEDS_ACTION -> answers.answerNextWaiting()
            toast.summary -> navigate(Route.Sessions)
        }
    }

    /** Opens the folder picker; the chosen directory is handed to [ProjectsState.addAt]. */
    fun requestAddProject() {
        paletteOpen = false
        projects.pickerOpen = true
    }

    private fun showSessionTerminal(ccmSessionId: String) {
        openSession(ccmSessionId)
        backend.terminal.focus(ccmSessionId)
    }

    fun refreshBackgroundAgents(projectId: Long) {
        val path: Path = projects.find(projectId)?.path ?: return
        if (!refreshingAgents.add(projectId)) return
        scope.launch {
            try {
                guarded { backgroundAgents[projectId] = io { backend.backgroundAgents.list(path) } }
            } finally {
                refreshingAgents.remove(projectId)
            }
        }
    }

    val terminal: TerminalPort get() = backend.terminal

    val dataDir: Path get() = backend.dataDir

    suspend fun activityOf(ccmSessionId: String): List<ActivityEntry> =
        try {
            io { backend.hooks.recent(ccmSessionId, ACTIVITY_LIMIT) }
        } catch (e: RuntimeException) {
            log.warn("Could not read the activity of session {}: {}", ccmSessionId, e.javaClass.simpleName)
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
        gits.retain(projectIds)
        backgroundAgents.keys.retainAll(projectIds)
        runs.retainProjects(projectIds)
        (route as? Route.Project)?.takeIf { it.id !in projectIds }?.let { route = Route.Overview }
        sessionReload.request()
    }

    /** A group shows the worst status over its own sessions and those of its member projects. */
    private fun statusOf(project: Project): AgentStatus =
        (listOf(project.id) + projects.membersOf(project.id).map { it.id }).maxOf { backend.sessions.projectStatus(it) }

    private suspend fun loadSessions() {
        val registered = projects.all
        val (sessions, statuses) = io {
            backend.sessions.sessions() to registered.associate { it.id to statusOf(it) }
        }
        val previous = live.sessions.associate { it.ccmSessionId to it.status }
        live = LiveSessions(sessions, statuses)
        agents.prune(sessions.map { it.ccmSessionId })
        notifications.dismissResolvedAttention(previous, sessions)
        projectsBecameIdle(previous, sessions).forEach { gits.refresh(it) }
        if (gits.anyReviewTurnEnded(previous, sessions) { id -> queue.of(id).map(QueuedPrompt::id) }) {
            notifications.toast(NotificationKind.INFO, "Agent finished", "Re-check review comments")
        }
    }

    private suspend fun guarded(onError: (String) -> Unit = notifications::reportError, block: suspend () -> Unit) = runGuarded(onError, block)
}
