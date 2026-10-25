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
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class HealthCheck { IDLE, RUNNING, DONE, FAILED }

sealed interface RawDetails {
    data object Loading : RawDetails
    data class Loaded(val text: String) : RawDetails
    data class Failed(val message: String) : RawDetails
}

const val ADD_SERVER_KEY = "mcp:add"

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
                loadFailure = failureOf("Could not read MCP servers", e)
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
        mutations.run("Adding $name", ADD_SERVER_KEY, { service.add(name, json, target, projectPath); service.servers(projectPath) }) {
            servers = it
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
            mutations.run("Removing ${entry.name}", entry.key, { service.remove(entry.name, target, projectPath); service.servers(projectPath) }) {
                servers = it
                details.remove(entry.key)
                if (selected == entry.key) selected = null
            }
        })
    }
}
