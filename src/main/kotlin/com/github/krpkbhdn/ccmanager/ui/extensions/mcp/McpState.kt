package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth
import com.github.krpkbhdn.ccmanager.extension.mcp.McpServerConfig
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.extensions.MutationRunner
import com.github.krpkbhdn.ccmanager.ui.extensions.label
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.ExtensionFailure
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.UNEXPECTED_ERROR
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.failureOf
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.awt.Desktop
import java.net.URI
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext

enum class HealthCheck { IDLE, RUNNING, DONE, FAILED }

sealed interface RawDetails {
    data object Loading : RawDetails
    data class Loaded(val text: String) : RawDetails
    data class Failed(val message: String) : RawDetails
}

const val ADD_SERVER_KEY = "mcp:add"
private const val LOAD_FAILURE_TITLE = "Could not read MCP servers"

/**
 * MCP servers of one project (or of the user when [projectPath] is null). The list loads at once; the slow health check
 * runs after it in the background. Server details may hold secrets: they stay in memory and are never logged.
 */
class McpState(
    private val service: McpService,
    private val projectPath: Path?,
    private val scope: CoroutineScope,
    private val confirm: (ConfirmRequest) -> Unit,
    private val notify: (NotificationKind, String, String) -> Unit,
) {
    private val mutations = MutationRunner(scope)
    var servers by mutableStateOf<List<McpServerConfig>>(emptyList())
        private set
    var health by mutableStateOf<List<McpHealth>>(emptyList())
        private set
    var healthCheck by mutableStateOf(HealthCheck.IDLE)
        private set
    var healthMessage by mutableStateOf("")
        private set
    var loggedIn by mutableStateOf<Set<String>>(emptySet())
        private set
    var signInUrl by mutableStateOf<String?>(null)
        private set
    var signingIn by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadFailure by mutableStateOf<ExtensionFailure?>(null)
    var selected by mutableStateOf<String?>(null)
    var adding by mutableStateOf(false)
    val details = mutableStateMapOf<String, RawDetails>()
    val job get() = mutations.job
    var failure: ExtensionFailure?
        get() = mutations.failure
        set(value) { mutations.failure = value }
    private var healthRun = 0
    private var signInJob: Job? = null

    fun ensureLoaded() {
        if (!loaded && !loading) refresh()
    }

    fun refresh(withHealth: Boolean = true) {
        loading = true
        val seen = mutations.epoch
        scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { service.servers(projectPath) }
                if (seen == mutations.epoch) servers = fresh
                loadFailure = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                loadFailure = failureOf(LOAD_FAILURE_TITLE, e)
            } finally {
                loading = false
                loaded = true
            }
            if (withHealth) checkHealth()
        }
    }

    /** Health-checks every server in the background; a newer check supersedes a running one. */
    fun checkHealth() {
        val run = ++healthRun
        healthCheck = HealthCheck.RUNNING
        scope.launch {
            try {
                loggedIn = withContext(Dispatchers.IO) { service.loggedInServers() }
                val result = withContext(Dispatchers.IO) { service.health(projectPath) }
                if (run == healthRun) { health = result; healthCheck = HealthCheck.DONE }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                if (run == healthRun) { healthMessage = e.message ?: UNEXPECTED_ERROR; healthCheck = HealthCheck.FAILED }
            }
        }
    }

    fun statusOf(name: String): McpHealth? = health.firstOrNull { it.name == name }

    fun canAuthenticate(name: String) = signingIn == null && canAuthenticate(statusOf(name)?.status)

    fun canLogOut(name: String) = job == null && canLogOut(name, loggedIn)

    /** Runs `claude mcp login`, which waits for the browser; [cancelSignIn] interrupts the thread and so kills the process. */
    fun signIn(name: String) {
        if (signingIn != null) return
        signingIn = name
        failure = null
        signInJob = scope.launch {
            try {
                withContext(Dispatchers.IO) { runInterruptible { service.login(name, projectPath) { signInUrl = it } } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                failure = failureOf("Signing in to $name", e)
            } finally {
                signingIn = null
                signInUrl = null
                signInJob = null
                recheck(name)
            }
        }
    }

    fun cancelSignIn() {
        signInJob?.cancel()
    }

    /** Fallback when the browser did not open on its own; only web addresses are handed to the system. */
    fun openSignInPage() {
        val url = signInUrl?.takeIf { it.startsWith("https://") || it.startsWith("http://") } ?: return
        try {
            Desktop.getDesktop().browse(URI(url))
        } catch (e: Exception) {
            notify(NotificationKind.ERROR, "Could not open the sign-in page", "Open your browser and try signing in again.")
        }
    }

    fun requestLogOut(entry: McpEntry) {
        val message = "Clears the stored sign-in of ${entry.name}. You will have to authenticate again to use it."
        confirm(ConfirmRequest("Log out of ${entry.displayName}?", message, "Log out") {
            mutations.run("Logging out of ${entry.displayName}", entry.key, { service.logout(entry.name, projectPath) }) { recheck(entry.name) }
        })
    }

    /** Forgets the stale status of [name] so it shows as checking, then re-checks health, which also re-reads the stored sign-ins. */
    private fun recheck(name: String) {
        health = health.filterNot { it.name == name }
        checkHealth()
    }

    fun loadDetails(entry: McpEntry) {
        if (details[entry.key] is RawDetails.Loaded || details[entry.key] == RawDetails.Loading) return
        details[entry.key] = RawDetails.Loading
        scope.launch {
            details[entry.key] = try {
                RawDetails.Loaded(withContext(Dispatchers.IO) { service.details(entry.name, projectPath) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                RawDetails.Failed(e.message ?: UNEXPECTED_ERROR)
            }
        }
    }

    fun add(name: String, json: String, target: ExtensionScope) {
        mutations.runThenReload("Adding $name", ADD_SERVER_KEY, LOAD_FAILURE_TITLE, { service.add(name, json, target, projectPath) }, { service.servers(projectPath) }, { loadFailure = it }, { servers = it }) {
            adding = false
            selected = mcpKey(target, name)
            notify(NotificationKind.FINISHED, "Added $name", "Available to new sessions, ${target.label()} scope.")
            checkHealth()
        }
    }

    fun requestRemove(entry: McpEntry) {
        val target = entry.scope ?: return
        val message = "Removes ${entry.name} from ${target.cliValue()} scope. Running sessions keep it until they restart."
        confirm(ConfirmRequest("Remove ${entry.name}?", message, "Remove") {
            mutations.runThenReload("Removing ${entry.name}", entry.key, LOAD_FAILURE_TITLE, { service.remove(entry.name, target, projectPath) }, { service.servers(projectPath) }, { loadFailure = it }, { servers = it }) {
                details.remove(entry.key)
                if (selected == entry.key) selected = null
            }
        })
    }
}
