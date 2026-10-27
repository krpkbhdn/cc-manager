package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.author.SessionHandoff
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val APPLY_TIMEOUT_MILLIS = 6_000L
private const val APPLY_POLL_MILLIS = 200L
private const val SWITCH_MODEL_HINT = "If claude asks \"Switch model?\", answer in the terminal."
private const val BUSY_MESSAGE = "The session is not ready for a command yet. Try again in a moment."

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
) {
    var launching by mutableStateOf(false)
        private set
    var modelApplying by mutableStateOf(false)
        private set

    val sessionTeams = mutableStateMapOf<String, Team>()
    /** The team each project's latest session started with: claude does not persist --agents, so Continue/Resume must pass it again. */
    private val lastTeams = mutableMapOf<Long, Team>()
    private val attachedSessions = mutableStateSetOf<String>()
    private val commandWriter = SessionCommandWriter(backend.terminal)

    fun isAttached(ccmSessionId: String) = ccmSessionId in attachedSessions

    /** Sessions whose last command was not applied: claude may be showing "Switch model?", so Ctrl+C must not go into it. */
    private val unconfirmed = mutableSetOf<String>()

    fun launch(projectId: Long, mode: LaunchMode) {
        val team = (if (mode == LaunchMode.NEW) null else lastTeams[projectId]) ?: defaultTeam(projectId)
        launchSession(projectId, team) {
            if (team == null) backend.launcher.launch(projectId, mode) else backend.launcher.launch(projectId, mode, team)
        }
    }

    /** Starts a lead session for [team] with [task] as its first prompt; [onStarted] runs only when the session really started. */
    fun launchTeam(projectId: Long, team: Team, task: String, onStarted: () -> Unit) =
        launchSession(projectId, team, onStarted) { backend.launcher.launchTeam(projectId, team, task) }

    /** Starts the session that finishes the author's setup in [projectId]; [onStarted] runs only when the session really started. */
    fun launchSetup(projectId: Long, handoff: SessionHandoff, onStarted: () -> Unit) =
        launchSession(projectId, onStarted = onStarted) { backend.launcher.launchSetup(projectId, handoff.firstMessage(), handoff.profileDirectory()) }

    private fun launchSession(projectId: Long, team: Team? = null, onStarted: () -> Unit = {}, start: () -> LaunchedSession) {
        if (launching) return
        launching = true
        scope.launch {
            guarded {
                val session = io(start)
                onStarted()
                team?.let {
                    sessionTeams[session.ccmSessionId()] = it
                    lastTeams[projectId] = it
                }
                backend.terminal.open(session)
                onOpened(projectId, session.ccmSessionId())
                showProject(projectId)
            }
            launching = false
        }
    }

    /**
     * Types [command] into the running [session] and shows "applying" until the statusLine reports [reached] or a few seconds
     * pass; claude refreshes the statusLine on its own after /model and /effort, but not always visibly different.
     */
    fun applyToSession(session: SessionSnapshot, command: String, reached: (SessionUsage?) -> Boolean) {
        if ((modelTarget(session, modelApplying, isAttached(session.ccmSessionId)) as? ModelTarget.Running)?.blockedReason != null) return
        if (unconfirmed.remove(session.ccmSessionId)) return reportError(SWITCH_MODEL_HINT)
        modelApplying = true
        scope.launch {
            guarded {
                if (!commandWriter.run(session.ccmSessionId, command)) return@guarded reportError(BUSY_MESSAGE)
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
            modelApplying = false
        }
    }

    /** Opens Claude's own model switcher (Alt+P) inside the session's terminal and moves the keyboard there. */
    fun openClaudeModelPicker(session: SessionSnapshot) {
        if ((modelTarget(session, modelApplying, isAttached(session.ccmSessionId)) as? ModelTarget.Running)?.blockedReason != null) return
        commandWriter.openModelPicker(session.ccmSessionId)
        backend.terminal.focus(session.ccmSessionId)
    }

    fun attach(projectId: Long, backgroundId: String) {
        scope.launch {
            guarded {
                val session = io { backend.launcher.attach(projectId, backgroundId) }
                attachedSessions.add(session.ccmSessionId())
                backend.terminal.open(session)
                onOpened(projectId, session.ccmSessionId())
            }
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
        sessionTeams.remove(ccmSessionId)
        unconfirmed.remove(ccmSessionId)
        onClosed(projectId, ccmSessionId)
        scope.launch { guarded { io { backend.terminal.close(ccmSessionId) } } }
    }

    fun send(ccmSessionId: String, keys: String) {
        if (keys.endsWith(Keys.ENTER)) commandBlockedReason(live().find(ccmSessionId), isAttached(ccmSessionId))?.let { return reportError(it) }
        scope.launch {
            guarded {
                val sent = if (keys.endsWith(Keys.ENTER)) commandWriter.run(ccmSessionId, keys.removeSuffix(Keys.ENTER)) else { backend.terminal.send(ccmSessionId, keys); true }
                if (!sent) reportError(BUSY_MESSAGE)
            }
        }
    }

    private suspend fun guarded(block: suspend () -> Unit) = runGuarded(reportError, block)
}
