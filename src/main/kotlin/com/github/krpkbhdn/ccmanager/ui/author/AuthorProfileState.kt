package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorInstallReport
import com.github.krpkbhdn.ccmanager.author.AuthorProfileService
import com.github.krpkbhdn.ccmanager.author.SessionHandoff
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val NO_PROJECT_TITLE = "Add a project first"
private const val NO_PROJECT_BODY = "Claude finishes the setup there"
private const val PROJECT_GONE_TITLE = "Project not found"
private const val PROJECT_GONE_BODY = "The project was removed, nothing was installed"

/** The offer: components waiting for a decision, each with its checkbox. */
class AuthorOffer(val entries: List<AuthorEntry>) {
    val checked = mutableStateMapOf<AuthorComponent, Boolean>().apply { this@AuthorOffer.entries.forEach { put(it.component(), !replacesEdits(it)) } }

    fun chosen() = entries.filter { checked[it.component()] == true }

    fun unchosen() = entries.filterNot { checked[it.component()] == true }
}

/** The configuration dialog: every component with its status, for the user scope and optionally one project. */
class AuthorManage(val projectId: Long?, val entries: List<AuthorEntry>)

/** What an install left for the user: report lines and MCP servers to sign in to. */
class AuthorReport(val lines: List<String>, val signIns: List<AuthorSignIn>)

class AuthorSignIn(val server: String, val mcp: McpState)

/**
 * The author's bundled setup in the UI: offers it at start and when a project is opened (each project once per run),
 * lets the user install or update it any time, and starts the Claude session that finishes it. Work runs off the UI thread.
 */
class AuthorProfileState(
    private val service: AuthorProfileService,
    private val scope: CoroutineScope,
    private val sessionProject: () -> Project?,
    private val projectOf: (Long) -> Project?,
    private val launchSetup: (Long, SessionHandoff, () -> Unit) -> Unit,
    private val mcpOf: (Path?) -> McpState,
    private val reportError: (String) -> Unit,
    private val toast: (NotificationKind, String, String) -> Unit,
) {
    var offer by mutableStateOf<AuthorOffer?>(null)
        private set
    var manage by mutableStateOf<AuthorManage?>(null)
        private set
    var report by mutableStateOf<AuthorReport?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    private val askedProjects = mutableSetOf<Long>()
    private var startOfferPending = false

    private val dialogOpen get() = offer != null || manage != null || report != null

    val sessionProjectName: String? get() = sessionProject()?.name

    fun offerAtStart() {
        startOfferPending = true
        launchGuarded {
            try {
                showOffer(null)
            } finally {
                startOfferPending = false
            }
        }
    }

    /** Skipped, and not remembered, while another dialog or the start offer is in the way: the project is offered on its next visit. */
    fun offerForProject(projectId: Long) {
        if (dialogOpen || startOfferPending || projectId in askedProjects) return
        launchGuarded { showOffer(projectId) }
    }

    fun openManage(projectId: Long? = null) = launchGuarded { manage = AuthorManage(projectId, io { service.entries(projectId) }) }

    fun install(chosen: List<AuthorEntry>, declined: List<AuthorEntry> = emptyList()) {
        if (busy) return
        busy = true
        scope.launch {
            try {
                runGuarded(reportError) { perform(chosen, declined) }
            } finally {
                busy = false
            }
        }
    }

    fun declineAll() {
        val declined = offer?.entries ?: return
        offer = null
        launchGuarded { io { service.decline(declined) } }
    }

    fun closeOffer() {
        if (!busy) offer = null
    }

    fun closeManage() {
        if (!busy) manage = null
    }

    fun closeReport() {
        report = null
    }

    private suspend fun showOffer(projectId: Long?) {
        val wanted = io { service.entries(projectId) }
            .filter { it.status().offered() && (projectId == null || it.component() == AuthorComponent.PROJECT_SETUP) }
        val blocked = dialogOpen || (projectId != null && startOfferPending)
        if (wanted.isEmpty() || blocked) return
        offer = AuthorOffer(wanted)
        projectId?.let(askedProjects::add)
    }

    private suspend fun perform(chosen: List<AuthorEntry>, declined: List<AuthorEntry>) {
        val projectEntry = chosen.firstOrNull { it.component() == AuthorComponent.PROJECT_SETUP }
        val project = if (projectEntry == null) sessionProject() else projectOf(projectEntry.projectId())
        if (projectEntry != null && project == null) {
            return toast(NotificationKind.ERROR, PROJECT_GONE_TITLE, PROJECT_GONE_BODY)
        }
        if (project == null && chosen.any { it.component() != AuthorComponent.TEAMS }) {
            return toast(NotificationKind.INFO, NO_PROJECT_TITLE, NO_PROJECT_BODY)
        }
        if (declined.isNotEmpty()) io { service.decline(declined) }
        val result = io { service.install(chosen, project?.path) }
        offer = null
        result.handoff().ifPresent { launchSetup(requireNotNull(project).id, it) { markInstalled(result.awaitingSession()) } }
        showResult(result, project)
        refreshManage()
    }

    private fun markInstalled(entries: List<AuthorEntry>) {
        scope.launch {
            runGuarded(reportError) {
                io { service.markInstalled(entries) }
                refreshManage()
            }
        }
    }

    private suspend fun refreshManage() {
        manage?.let { open -> manage = AuthorManage(open.projectId, io { service.entries(open.projectId) }) }
    }

    private fun showResult(result: AuthorInstallReport, project: Project?) {
        val lines = result.components().flatMap { it.outcome().report() }
        val mcpByPath = HashMap<Path?, McpState>()
        val signIns = result.components().flatMap { component ->
            val path = if (component.component() == AuthorComponent.PROJECT_SETUP) project?.path else null
            component.outcome().signInNeeded().map { AuthorSignIn(it, mcpByPath.getOrPut(path) { mcpOf(path) }) }
        }
        if (result.components().any { it.failed() } || signIns.isNotEmpty()) {
            report = AuthorReport(lines, signIns)
        } else {
            toast(NotificationKind.INFO, "Author configuration installed", project?.let { "Claude finishes the setup in ${it.name}" } ?: "Agent teams are ready")
        }
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        scope.launch { runGuarded(reportError, block) }
    }
}
