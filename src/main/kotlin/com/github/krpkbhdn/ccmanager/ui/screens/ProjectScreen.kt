package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.hook.ActivityEntry
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.review.ReviewScreen
import com.github.krpkbhdn.ccmanager.ui.web.WebScreen
import com.github.krpkbhdn.ccmanager.ui.run.RunControlActions
import com.github.krpkbhdn.ccmanager.ui.run.RunTab
import com.github.krpkbhdn.ccmanager.ui.run.RunTabActions
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.queue.QueueControl
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.git.GitSystemActions
import com.github.krpkbhdn.ccmanager.ui.git.GitTab
import com.github.krpkbhdn.ccmanager.ui.git.changes.ReviewTarget
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.ProjectView
import com.github.krpkbhdn.ccmanager.ui.state.byUrgency
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.state.sessionsWorkingIn
import com.github.krpkbhdn.ccmanager.ui.teams.TeamRoleStrip
import com.github.krpkbhdn.ccmanager.ui.teams.runningAgentTypes
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val ENDED_SESSION_LABEL = "Ended session"

/** A project: header, session tabs with the terminal on top, and the agents rail on the right. */
@Composable
fun ProjectScreen(state: AppState, project: Project, modifier: Modifier = Modifier) {
    val view = state.viewOf(project.id)
    val gitState = state.gits.of(project.id)
    LaunchedEffect(project.id) { state.refreshBackgroundAgents(project.id) }
    val mainGit = state.gits.mainOf(project.id)
    DisposableEffect(project.id, gitState, mainGit) {
        // The main folder stays watched while a worktree is shown: the sidebar and the context selector read it.
        val unsubscribes = listOfNotNull(gitState, mainGit).distinct().map { it.attach() }
        onDispose { unsubscribes.forEach(Runnable::run) }
    }
    val windowFocused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(windowFocused, gitState, mainGit) {
        if (!windowFocused) return@LaunchedEffect
        gitState?.refresh()
        if (mainGit !== gitState) {
            mainGit?.refresh()
            mainGit?.loadWorktrees()
        }
    }
    LaunchedEffect(project.id, windowFocused) { if (windowFocused) state.runs.track(project.id) }
    Column(modifier.fillMaxSize()) {
        ProjectHeader(
            project = project,
            status = state.live.statusOf(project.id),
            ideAvailable = state.environment.ideAvailable,
            launching = state.sessions.launching,
            gitSummary = gitState?.summary,
            actions = ProjectHeaderActions(
                onLaunch = { state.sessions.launch(project.id, it) },
                onStartTask = { state.tasks.open(project.id) },
                onStartTaskFromTemplate = { state.tasks.quickStartProjectId = project.id },
                onOpenFolder = { state.environment.openFolder(project) },
                onOpenIde = { state.environment.openIde(project) },
                onShowGit = { state.openGit(project.id) },
                onShowChanges = { state.openGit(project.id, GitSubView.CHANGES) },
                onOpenSettings = { state.extensions.openProjectSettings(project) },
            ),
            runEntries = state.runs.entriesOf(project.id),
            runSelected = state.runs.selectedOf(project.id),
            runActions = RunControlActions(
                onSelect = { state.runs.select(project.id, it) },
                onRun = state.runs::run,
                onStop = state.runs::stop,
                onAdd = { state.runs.openDialog(project.id) },
                onEdit = { id -> state.runs.openDialog(project.id, state.runs.entriesOf(project.id).firstOrNull { it.id == id }?.configuration) },
            ),
        )
        Row(Modifier.fillMaxSize()) {
            ProjectMainArea(state, project, view, Modifier.weight(1f))
            AnimatedVisibility(
                visible = state.settings.agentsOpen,
                enter = expandHorizontally(Motion.slow()) + fadeIn(Motion.base()),
                exit = shrinkHorizontally(Motion.base()) + fadeOut(Motion.fast()),
            ) {
                AgentsPanel(
                    sessions = byUrgency(state.live.of(project.id)),
                    teams = state.sessions.sessionTeams,
                    selectedSessionId = (view.selected as? ProjectTab.Session)?.ccmSessionId,
                    finishedOpen = state.agents::isFinishedOpen,
                    now = { state.now },
                    background = state.backgroundAgents[project.id],
                    actions = AgentsPanelActions(
                        onOpenSession = { state.select(project.id, ProjectTab.Session(it)) },
                        onRefreshBackground = { state.refreshBackgroundAgents(project.id) },
                        onAttach = { state.sessions.attach(project.id, it) },
                        onOpenAgent = state.agents::openLog,
                        onToggleFinished = state.agents::toggleFinished,
                    ),
                )
            }
        }
    }
}

@Composable
private fun ProjectMainArea(state: AppState, project: Project, view: ProjectView, modifier: Modifier) {
    val onSession = view.selected is ProjectTab.Session
    Column(modifier.fillMaxSize()) {
        ProjectTabStrip(
            tabs = tabSpecs(state, view, project),
            selected = view.selected,
            onSelect = { state.select(project.id, it) },
            onClose = { closeTab(state, project.id, it) },
            onNewSession = { state.sessions.launch(project.id, LaunchMode.NEW) },
            shellMenu = if (project.group) emptyList() else shellMenu(state, project.id),
        ) {
            val shown = (view.selected as? ProjectTab.Session)?.ccmSessionId
            if (shown != null) {
                QueueControl(state, project, shown)
                Spacer(Modifier.width(Spacing.sm))
            }
            ModelControl(state, project, shown?.let { state.live.find(it) })
            Spacer(Modifier.width(Spacing.sm))
            if (onSession) {
                IconButton(Icons.Zap, state.prefs::toggleQuickBar, tooltip = "Quick actions", hint = AppShortcut.TOGGLE_QUICK_ACTIONS.hint(), active = state.settings.quickBarOpen)
            }
            IconButton(
                if (state.settings.agentsOpen) Icons.PanelRightClose else Icons.PanelRightOpen,
                state.prefs::toggleAgents,
                Modifier.padding(start = Spacing.xs),
                tooltip = if (state.settings.agentsOpen) "Hide agents" else "Show agents",
                hint = AppShortcut.TOGGLE_AGENTS.hint(),
            )
        }
        HorizontalRule()
        MainContent(state, project, view)
    }
}

private fun closeTab(state: AppState, projectId: Long, tab: ProjectTab) {
    when (tab) {
        is ProjectTab.Session -> state.sessions.requestClose(projectId, tab.ccmSessionId)
        is ProjectTab.Shell -> state.shells.close(projectId, tab.terminalId)
        else -> Unit
    }
}

private fun shellMenu(state: AppState, projectId: Long): List<MenuEntry> =
    state.shells.available.map { shell ->
        MenuEntry(shell.name, Icons.SquareTerminal, hint = "Default".takeIf { shell == state.shells.default }) { state.shells.open(projectId, shell) }
    }

@Composable
private fun MainContent(state: AppState, project: Project, view: ProjectView) {
    val tab = view.selected
    if (tab is ProjectTab.Session) {
        SessionTab(state, project.id, tab.ccmSessionId)
        return
    }
    if (tab is ProjectTab.Shell) {
        TerminalPane(state, tab.terminalId)
        return
    }
    Crossfade(tab, animationSpec = Motion.base(), label = "projectTab") { shown ->
        when (shown) {
            ProjectTab.Git -> if (project.group) GroupMembersTab(state, project) else GitTabHost(state, project)
            ProjectTab.Run -> RunTab(
                state.runs.selectedOf(project.id), state.terminal,
                RunTabActions(state.runs::run, state.runs::stop) { state.runs.openDialog(project.id) },
            )
            ProjectTab.Review -> ReviewScreen(project.id, state.reviews)
            ProjectTab.Web -> WebScreen(project.id, state.web)
            else -> ActivityTabHost(state, view, project)
        }
    }
}

/** A group has no repository, so its Git tab slot (ProjectTab.Git) shows the members instead. */
private fun tabSpecs(state: AppState, view: ProjectView, project: Project): List<TabSpec> {
    val sessions = view.openSessions.map { id ->
        val snapshot = state.live.find(id)
        TabSpec(ProjectTab.Session(id), snapshot?.let(::sessionTitle) ?: ENDED_SESSION_LABEL, snapshot?.status ?: AgentStatus.OFFLINE, null, closable = true, badge = state.gits.worktreeBadgeOf(project.id, id))
    }
    val shells = view.openShells.map { id -> TabSpec(ProjectTab.Shell(id), state.shells.labelOf(id), null, Icons.SquareTerminal, closable = true) }
    val git = if (project.group) TabSpec(ProjectTab.Git, "Members", null, Icons.FolderTree, closable = false)
    else TabSpec(ProjectTab.Git, "Git", null, Icons.Branch, closable = false)
    val run = TabSpec(ProjectTab.Run, "Run", null, Icons.Play, closable = false).takeIf { !project.group }
    val pending = state.reviews.pendingCount(project.id)
    val review = TabSpec(ProjectTab.Review, "Review", null, Icons.Eye, closable = false, badge = pending.takeIf { it > 0 }?.toString()).takeIf { !project.group }
    val webPending = state.web.pendingCount(project.id)
    val web = TabSpec(ProjectTab.Web, "Web", null, Icons.Globe, closable = false, badge = webPending.takeIf { it > 0 }?.toString()).takeIf { !project.group }
    return sessions + shells + listOfNotNull(git, run, review, web, TabSpec(ProjectTab.Activity, "Activity", null, Icons.Activity, closable = false))
}

@Composable
private fun SessionTab(state: AppState, projectId: Long, ccmSessionId: String) {
    Column(Modifier.fillMaxSize()) {
        val live = state.live.find(ccmSessionId)
        val team = if (live != null) state.sessions.sessionTeams[ccmSessionId] else state.teams.defaultTeamOf(projectId)
        TeamRoleStrip(team, if (live != null) live.runningAgentTypes() else emptySet())
        QuickActionsBar(state.settings.quickBarOpen, enabled = state.live.find(ccmSessionId) != null, onSend = { state.sessions.send(ccmSessionId, it) })
        TerminalPane(state, ccmSessionId) {
            if (state.proposals.hiddenCount(ccmSessionId) > 0) {
                AppButton(
                    "Claude is waiting - Answer", { state.proposals.reopen(ccmSessionId) },
                    Modifier.align(Alignment.TopEnd).padding(end = Spacing.lg, top = Spacing.xs),
                    variant = ButtonVariant.PRIMARY, icon = Icons.Message, compact = true,
                )
            }
        }
    }
}

@Composable
internal fun TerminalPane(state: AppState, terminalId: String, overlay: @Composable BoxScope.() -> Unit = {}) {
    Box(Modifier.fillMaxSize().background(colors.terminal).padding(start = TERMINAL_INSET, top = TERMINAL_INSET)) {
        state.terminal.View(terminalId, Modifier.fillMaxSize())
        overlay()
    }
}

internal val TERMINAL_INSET = 10.dp

/** A review session starts in the worktree the Git tab shows, else in the project folder. */
private fun startReviewSession(state: AppState, project: Project, prompt: String, onStarted: (String) -> Unit) {
    val team = state.teams.defaultTeamOf(project.id)
    val worktree = state.gits.activeDirectoryOf(project.id)
    if (worktree == null) state.sessions.launchTask(project.id, team, prompt, onStarted = onStarted)
    else state.sessions.launchTaskIn(project.id, worktree, team, prompt, onNotStarted = state.notifications::reportError, onStarted = onStarted)
}

@Composable
private fun GitTabHost(state: AppState, project: Project) {
    val git = state.gits.of(project.id) ?: return
    val ideAvailable = state.environment.ideAvailable
    val system = remember(project, ideAvailable, git) {
        GitSystemActions(
            openFolder = { path -> state.environment.openDirectory(git.repo.resolve(path).parent ?: git.repo) },
            openInIde = { path -> state.environment.openInIde(git.repo.resolve(path)) },
            ideAvailable = ideAvailable,
        )
    }
    val review = ReviewTarget(
        sessions = { sessionsWorkingIn(state.live.of(project.id), state.sessions.sessionDirectories, state.gits.activeDirectoryOf(project.id), project.path) },
        preferred = { state.viewOf(project.id).focusedSession?.takeIf { state.live.find(it) != null } },
        send = state.queue::add,
        startAndSend = { prompt, onStarted -> startReviewSession(state, project, prompt, onStarted) },
    )
    GitTab(git, system, review, now = { state.now }, work = workContextActions(state, project))
}

@Composable
private fun ActivityTabHost(state: AppState, view: ProjectView, project: Project) {
    val target = view.focusedSession?.takeIf { state.live.find(it) != null } ?: byUrgency(state.live.of(project.id)).firstOrNull()?.ccmSessionId
    val lastEventAt = target?.let { state.live.find(it)?.lastEventAt }
    val entries by produceState(emptyList<ActivityEntry>(), target, lastEventAt) {
        value = target?.let { state.activityOf(it) }.orEmpty()
    }
    ActivityTab(entries, hasSession = target != null, onLaunch = { state.sessions.launch(project.id, it) }, launching = state.sessions.launching)
}
