package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.ui.git.model.sameFolder
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val APPLY_TIMEOUT_MILLIS = 6_000L
private const val APPLY_POLL_MILLIS = 200L
private const val SWITCH_MODEL_HINT = "If claude asks \"Switch model?\", answer in the terminal."
private const val DRAFT_MESSAGE = "You have unsent text in the terminal. Send or clear it, then change the model."
private const val DRAFT_MESSAGE_SEND = "You have unsent text in the terminal. Send or clear it first."
private const val BUSY_MESSAGE = "The session is not ready for a command yet. Try again in a moment."

private const val UNEXPECTED_LAUNCH_ERROR = "The session could not be started"
private const val LAUNCH_IN_PROGRESS_MESSAGE = "Another session is starting. Try again in a moment."

/** Starting, attaching, closing and commanding the Claude sessions of the projects. */
class SessionsState(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val live: () -> LiveSessions,
    private val usageOf: (String) -> SessionUsage?,
    private val defaultTeam: (projectId: Long) -> Team?,
    private val onOpened: (projectId: Long, ccmSessionId: String) -> Unit,
    private val onClosed: (projectId: Long, ccmSessionId: String) -> Unit,
    private val showProject: (Long) -> Unit,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val reportError: (String) -> Unit,
    private val projectPath: (projectId: Long) -> Path? = { null },
) {
    var launching by mutableStateOf(false)
        private set
    var modelApplying by mutableStateOf(false)
        private set

    val sessionTeams = mutableStateMapOf<String, Team>()

    /** The folder each session runs in: the project folder or one of its task worktrees. */
    val sessionDirectories = mutableStateMapOf<String, Path>()
    /** The team each project's latest session started with: claude does not persist --agents, so Continue/Resume must pass it again. */
    private val lastTeams = mutableMapOf<Long, Team?>()
    private val attachedSessions = mutableStateSetOf<String>()
    private val attachedBackgrounds = mutableMapOf<String, String>()
    private val attachingBackgrounds = mutableSetOf<String>()
    private val commandWriter = SessionCommandWriter(backend.terminal)
    private fun liveIn(directory: Path) = sessionDirectories.filterValues { sameFolder(it, directory) }.keys.mapNotNull { live().find(it) }

    /** A session that already ended by itself holds nothing, even while its tab is still open. */
    fun hasLiveIn(directory: Path) = liveIn(directory).any { it.status != AgentStatus.OFFLINE }

    /** Closes the sessions running in [directory] so nothing holds the folder while git removes it. */
    fun closeIn(directory: Path) = liveIn(directory).forEach { close(it.projectId, it.ccmSessionId) }

    fun isAttached(ccmSessionId: String) = ccmSessionId in attachedSessions

    /** Sessions whose last command was not applied: claude may be showing "Switch model?", so Ctrl+C must not go into it. */
    private val unconfirmed = mutableSetOf<String>()

    fun launch(projectId: Long, mode: LaunchMode) {
        val remembered = mode != LaunchMode.NEW && lastTeams.containsKey(projectId)
        val team = if (remembered) lastTeams[projectId] else defaultTeam(projectId)
        launchSession(projectId, team) {
            if (team == null) backend.launcher.launch(projectId, mode) else backend.launcher.launch(projectId, mode, team)
        }
    }

    /** Like [launch] in [directory], a git worktree of the project; the session still belongs to the project. */
    fun launchIn(projectId: Long, directory: Path, mode: LaunchMode) {
        val remembered = mode != LaunchMode.NEW && lastTeams.containsKey(projectId)
        val team = if (remembered) lastTeams[projectId] else defaultTeam(projectId)
        launchSession(projectId, team, directory = directory) { backend.launcher.launchIn(projectId, directory, mode, team) }
    }

    /** Like [launchTask] in [directory], a git worktree of the project. */
    fun launchTaskIn(projectId: Long, directory: Path, team: Team?, task: String, onNotStarted: (reason: String) -> Unit, onStarted: (ccmSessionId: String) -> Unit) =
        launchSession(projectId, team, onStarted, rememberTeam = true, onNotStarted = onNotStarted, directory = directory) { backend.launcher.launchTaskIn(projectId, directory, team, task) }

    /** Resumes the stored conversation [claudeSessionId]; if it is already live, its tab is focused instead of starting a second claude. */
    fun resume(projectId: Long, claudeSessionId: String, cwd: Path?) {
        live().sessions.firstOrNull { it.claudeSessionId == claudeSessionId }?.let {
            onOpened(it.projectId, it.ccmSessionId)
            showProject(it.projectId)
            return backend.terminal.focus(it.ccmSessionId)
        }
        val team = defaultTeam(projectId)
        launchSession(projectId, team) { backend.launcher.resume(projectId, claudeSessionId, cwd, team) }
    }

    /** Starts the general session outside any project. */
    fun launchAssistant(show: Boolean = true) = launchSession(ClaudeLauncher.ASSISTANT_PROJECT_ID, show = show) { backend.launcher.launchAssistant() }

    /** Starts a session with [task] as its first prompt, as a lead of [team] or, for null, without a team; [onStarted] gets the new session's id and runs only when it really started, [onNotStarted] only when it did not, getting the reason instead of the usual error message. */
    fun launchTask(projectId: Long, team: Team?, task: String, onNotStarted: ((reason: String) -> Unit)? = null, onStarted: (ccmSessionId: String) -> Unit) =
        launchSession(projectId, team, onStarted, rememberTeam = true, onNotStarted = onNotStarted) {
            if (team == null) backend.launcher.launchTask(projectId, task) else backend.launcher.launchTeam(projectId, team, task)
        }

    private fun launchSession(projectId: Long, team: Team? = null, onStarted: (String) -> Unit = {}, rememberTeam: Boolean = team != null, show: Boolean = true, onNotStarted: ((String) -> Unit)? = null, directory: Path? = null, start: () -> LaunchedSession) {
        if (launching) return (onNotStarted ?: reportError)(LAUNCH_IN_PROGRESS_MESSAGE)
        launching = true
        scope.launch {
            var started = false
            val onError = { message: String -> if (started || onNotStarted == null) reportError(message) else onNotStarted(message) }
            try {
                runGuarded(onError) {
                    val session = io(start)
                    started = true
                    try {
                        onStarted(session.ccmSessionId())
                        team?.let { sessionTeams[session.ccmSessionId()] = it }
                        if (rememberTeam) lastTeams[projectId] = team
                        openTab(session, projectId, directory)
                    } catch (e: Throwable) {
                        ProcessTree.kill(session.process())
                        backend.terminal.close(session.ccmSessionId())
                        throw e
                    }
                    if (show) showProject(projectId)
                }
            } catch (e: Throwable) {
                if (e !is CancellationException) onError(e.message ?: UNEXPECTED_LAUNCH_ERROR)
                throw e
            } finally {
                launching = false
            }
        }
    }

    /** Shows a session another component started (the Assistant's tools) and records its folder, so the worktree guards see it. */
    fun adopt(session: LaunchedSession) = openTab(session, session.projectId, null)

    private fun openTab(session: LaunchedSession, projectId: Long, directory: Path?) {
        (session.directory() ?: directory ?: projectPath(projectId))?.let { sessionDirectories[session.ccmSessionId()] = it }
        backend.terminal.open(session)
        onOpened(projectId, session.ccmSessionId())
    }

    /**
     * Types [command] into the running [session] and shows "applying" until the statusLine reports [reached] or a few seconds
     * pass; claude refreshes the statusLine on its own after /model and /effort, but not always visibly different.
     */
    fun applyToSession(session: SessionSnapshot, command: String, reached: (SessionUsage?) -> Boolean) {
        if ((modelTarget(session, modelApplying, isAttached(session.ccmSessionId)) as? ModelTarget.Running)?.blockedReason != null) return
        if (unconfirmed.remove(session.ccmSessionId)) return reportError(SWITCH_MODEL_HINT)
        if (backend.terminal.draftMayExist(session.ccmSessionId)) return reportError(DRAFT_MESSAGE)
        modelApplying = true
        scope.launch {
            try {
                guarded {
                    val typed = commandWriter.run(session.ccmSessionId, command, clearDraft = false) { backend.terminal.draftMayExist(session.ccmSessionId) }
                    if (!typed) return@guarded reportError(BUSY_MESSAGE)
                    backend.terminal.focus(session.ccmSessionId)
                    var waited = 0L
                    while (!reached(usageOf(session.ccmSessionId)) && waited < APPLY_TIMEOUT_MILLIS) {
                        delay(APPLY_POLL_MILLIS)
                        waited += APPLY_POLL_MILLIS
                    }
                    if (!reached(usageOf(session.ccmSessionId))) {
                        unconfirmed.add(session.ccmSessionId)
                        reportError(SWITCH_MODEL_HINT)
                    }
                }
            } finally {
                modelApplying = false
            }
        }
    }

    /** Opens Claude's own model switcher (Alt+P) inside the session's terminal and moves the keyboard there. */
    fun openClaudeModelPicker(session: SessionSnapshot) {
        if ((modelTarget(session, modelApplying, isAttached(session.ccmSessionId)) as? ModelTarget.Running)?.blockedReason != null) return
        commandWriter.openModelPicker(session.ccmSessionId)
        backend.terminal.focus(session.ccmSessionId)
    }

    fun attach(projectId: Long, backgroundId: String) {
        if (backgroundId in attachingBackgrounds || backgroundId in attachedBackgrounds.values) return
        attachingBackgrounds.add(backgroundId)
        scope.launch {
            guarded {
                val session = io { backend.launcher.attach(projectId, backgroundId) }
                attachedSessions.add(session.ccmSessionId())
                attachedBackgrounds[session.ccmSessionId()] = backgroundId
                openTab(session, projectId, null)
            }
            attachingBackgrounds.remove(backgroundId)
        }
    }

    fun requestClose(projectId: Long, ccmSessionId: String) {
        if (live().find(ccmSessionId) == null) return close(projectId, ccmSessionId)
        requestConfirm(
            ConfirmRequest(
                title = "Close session",
                message = "The Claude process of this session is still running and will be terminated.",
                confirmLabel = "Terminate",
            ) { close(projectId, ccmSessionId) },
        )
    }

    fun close(projectId: Long, ccmSessionId: String) {
        attachedSessions.remove(ccmSessionId)
        attachedBackgrounds.remove(ccmSessionId)
        sessionTeams.remove(ccmSessionId)
        sessionDirectories.remove(ccmSessionId)
        unconfirmed.remove(ccmSessionId)
        onClosed(projectId, ccmSessionId)
        scope.launch { guarded { io { backend.terminal.close(ccmSessionId) } } }
    }

    fun send(ccmSessionId: String, keys: String, onSent: () -> Unit = {}) {
        if (keys.endsWith(Keys.ENTER)) commandBlockedReason(live().find(ccmSessionId), isAttached(ccmSessionId))?.let { return reportError(it) }
        if (keys.endsWith(Keys.ENTER) && backend.terminal.draftMayExist(ccmSessionId)) return reportError(DRAFT_MESSAGE_SEND)
        scope.launch {
            guarded {
                val sent = if (keys.endsWith(Keys.ENTER)) commandWriter.run(ccmSessionId, keys.removeSuffix(Keys.ENTER), clearDraft = false) { backend.terminal.draftMayExist(ccmSessionId) } else { backend.terminal.send(ccmSessionId, keys); true }
                if (sent) onSent() else reportError(BUSY_MESSAGE)
            }
        }
    }

    /** Types a queued prompt without clearing the draft, unless a draft exists by the time it would be typed. */
    suspend fun sendQueued(ccmSessionId: String, text: String): Boolean =
        commandWriter.run(ccmSessionId, text, clearDraft = false) { backend.terminal.draftMayExist(ccmSessionId) }

    private suspend fun guarded(block: suspend () -> Unit) = runGuarded(reportError, block)
}
