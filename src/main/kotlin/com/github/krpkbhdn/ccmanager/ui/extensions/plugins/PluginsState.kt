package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.extension.ExtensionException
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.InstalledPlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginDetails
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginInstallResult
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService
import com.github.krpkbhdn.ccmanager.extension.plugin.ShownCommand
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.extensions.MutationRunner
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal const val UNEXPECTED_ERROR = "Something went wrong"

/** A failed CLI call: [title] says what was attempted, [output] is the CLI's own text (may be empty). */
data class ExtensionFailure(val title: String, val message: String, val output: String)

/** The running mutation; [key] matches the row or button it was started from. */
data class PluginJob(val label: String, val key: String)

enum class PendingAction(val gerund: String, val done: String) { INSTALL("Installing", "Installed"), UPDATE("Updating", "Updated") }

/** A marketplace command waiting for the user's explicit yes before the install or update is repeated. */
data class PendingCommand(val action: PendingAction, val pluginId: String, val scope: ExtensionScope, val projectPath: Path?, val command: ShownCommand)

sealed interface DetailsState {
    data object Loading : DetailsState
    data class Loaded(val details: PluginDetails) : DetailsState
    data class Failed(val message: String) : DetailsState
}

fun detailsKey(pluginId: String, projectPath: Path?) = "$pluginId|${projectPath ?: ""}"

fun installKey(pluginId: String) = "install:$pluginId"

fun InstalledPlugin.key() = "$id:$scope:$projectPath"

fun marketplaceKey(name: String) = "marketplace:$name"

const val ADD_MARKETPLACE_KEY = "marketplace:add"
const val UPDATE_ALL_KEY = "marketplace:all"

/**
 * Plugins and marketplaces for every extensions page, whatever its context. The catalogue loads once and is filtered by
 * the view; mutations run one at a time off the UI thread and a failure keeps the CLI output. Writes on [scope].
 */
class PluginsState(
    private val service: PluginService,
    private val scope: CoroutineScope,
    private val confirm: (ConfirmRequest) -> Unit,
    private val notify: (NotificationKind, String, String) -> Unit,
) {
    var installed by mutableStateOf<List<InstalledPlugin>>(emptyList())
        private set
    var marketplaces by mutableStateOf<List<Marketplace>>(emptyList())
        private set
    var catalog by mutableStateOf<List<AvailablePlugin>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var catalogLoading by mutableStateOf(false)
        private set
    var loadFailure by mutableStateOf<ExtensionFailure?>(null)
        private set
    var catalogFailure by mutableStateOf<ExtensionFailure?>(null)
        private set
    private val mutations = MutationRunner(scope)
    val job get() = mutations.job
    var failure: ExtensionFailure?
        get() = mutations.failure
        set(value) { mutations.failure = value }
    var pending by mutableStateOf<PendingCommand?>(null)
        private set
    private var catalogRun = 0
    var tab by mutableStateOf(PluginsTab.INSTALLED)
    var selectedInstalled by mutableStateOf<String?>(null)
    var selectedAvailable by mutableStateOf<String?>(null)
    val details = mutableStateMapOf<String, DetailsState>()

    fun ensureLoaded() {
        if (!loaded && !loading) refresh(reloadCatalog = false)
    }

    fun refresh(reloadCatalog: Boolean = true) {
        loading = true
        val seen = mutations.epoch
        scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { snapshot() }
                if (seen == mutations.epoch) apply(fresh)
                loadFailure = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                loadFailure = failureOf("Could not read plugins", e)
            } finally {
                loading = false
                loaded = true
            }
        }
        loadCatalog(force = reloadCatalog)
    }

    fun loadCatalog(force: Boolean = false) {
        if (catalogLoading || (!force && catalog.isNotEmpty())) return
        startCatalogLoad(force)
    }

    /** Starts a load even while one runs; only the newest load applies its result. */
    private fun startCatalogLoad(force: Boolean) {
        val run = ++catalogRun
        catalogLoading = true
        scope.launch {
            try {
                val loadedCatalog = withContext(Dispatchers.IO) { if (force) service.refreshAvailable() else service.available() }
                if (run == catalogRun) { catalog = loadedCatalog; catalogFailure = null }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                if (run == catalogRun) catalogFailure = failureOf("Could not load the marketplace", e)
            } finally {
                if (run == catalogRun) catalogLoading = false
            }
        }
    }

    fun loadDetails(pluginId: String, projectPath: Path? = null) {
        val key = detailsKey(pluginId, projectPath)
        val known = details[key]
        if (known != null && known !is DetailsState.Failed) return
        details[key] = DetailsState.Loading
        scope.launch {
            details[key] = try {
                DetailsState.Loaded(withContext(Dispatchers.IO) { service.details(pluginId, projectPath) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                DetailsState.Failed(e.message ?: UNEXPECTED_ERROR)
            }
        }
    }

    fun setEnabled(plugin: InstalledPlugin, enabled: Boolean) {
        val label = "${if (enabled) "Enabling" else "Disabling"} ${pluginName(plugin.id)}"
        act(label, plugin.key(), call = { service.setEnabled(plugin.id, plugin.scope, plugin.projectPath, enabled) })
    }

    fun install(pluginId: String, target: ExtensionScope, projectPath: Path?) =
        startCommand(PendingAction.INSTALL, pluginId, target, projectPath, installKey(pluginId)) { service.install(pluginId, target, projectPath) }

    fun update(plugin: InstalledPlugin) =
        startCommand(PendingAction.UPDATE, plugin.id, plugin.scope, plugin.projectPath, plugin.key()) { service.update(plugin.id, plugin.scope, plugin.projectPath) }

    fun confirmPending() {
        if (job != null) return
        val command = pending ?: return
        pending = null
        val key = if (command.action == PendingAction.INSTALL) installKey(command.pluginId) else keyOfInstalled(command)
        startCommand(command.action, command.pluginId, command.scope, command.projectPath, key) {
            when (command.action) {
                PendingAction.INSTALL -> service.installAccepting(command.pluginId, command.scope, command.projectPath, command.command)
                PendingAction.UPDATE -> service.updateAccepting(command.pluginId, command.scope, command.projectPath, command.command)
            }
        }
    }

    fun dismissLoadFailure() {
        loadFailure = null
    }

    fun dismissPending() {
        pending = null
    }

    fun requestUninstall(plugin: InstalledPlugin) {
        val name = pluginName(plugin.id)
        val message = "Removes $name from ${plugin.scope.cliValue()} scope. You can install it again from the marketplace."
        confirm(ConfirmRequest("Uninstall $name?", message, "Uninstall") {
            act("Uninstalling $name", plugin.key(), call = { service.uninstall(plugin.id, plugin.scope, plugin.projectPath) })
        })
    }

    fun addMarketplace(source: String, target: ExtensionScope = ExtensionScope.USER, projectPath: Path? = null, onAdded: () -> Unit = {}) {
        act("Adding ${source.trim()}", ADD_MARKETPLACE_KEY, reloadCatalog = true, call = { service.addMarketplace(source.trim(), target, projectPath) }, onSuccess = { onAdded() })
    }

    fun updateMarketplace(name: String?) {
        val label = if (name == null) "Updating all marketplaces" else "Updating $name"
        act(label, if (name == null) UPDATE_ALL_KEY else marketplaceKey(name), reloadCatalog = true, call = { service.updateMarketplace(name) })
    }

    fun requestRemoveMarketplace(marketplace: Marketplace) {
        val message = "Its plugins leave the marketplace list. Plugins already installed from it stay installed."
        confirm(ConfirmRequest("Remove ${marketplace.name}?", message, "Remove") {
            act("Removing ${marketplace.name}", marketplaceKey(marketplace.name), reloadCatalog = true, call = { service.removeMarketplace(marketplace.name) })
        })
    }

    private fun keyOfInstalled(command: PendingCommand) =
        installed.firstOrNull { it.id == command.pluginId && it.scope == command.scope && it.projectPath == command.projectPath }?.key() ?: installKey(command.pluginId)

    private fun startCommand(action: PendingAction, pluginId: String, target: ExtensionScope, projectPath: Path?, key: String, call: () -> PluginInstallResult) {
        val name = pluginName(pluginId)
        act("${action.gerund} $name", key, call = call) { result ->
            val command = result.pendingCommand
            if (command != null) {
                pending = PendingCommand(action, pluginId, target, projectPath, command)
            } else {
                notify(NotificationKind.FINISHED, "${action.done} $name", result.message.orEmpty())
            }
        }
    }

    private class Snapshot(val installed: List<InstalledPlugin>, val marketplaces: List<Marketplace>)

    private fun snapshot() = Snapshot(service.installed(), service.marketplaces())

    private fun apply(snapshot: Snapshot) {
        installed = snapshot.installed
        marketplaces = snapshot.marketplaces
    }

    private fun <T> act(label: String, key: String, reloadCatalog: Boolean = false, call: () -> T, onSuccess: (T) -> Unit = {}) {
        mutations.runThenReload(label, key, "Could not read plugins", call, ::snapshot, { loadFailure = it }, ::apply) { result ->
            details.clear()
            onSuccess(result)
            if (reloadCatalog) {
                catalog = emptyList()
                startCatalogLoad(force = true)
            }
        }
    }
}

internal fun failureOf(title: String, e: RuntimeException) =
    ExtensionFailure(title, e.message ?: UNEXPECTED_ERROR, (e as? ExtensionException)?.output().orEmpty())
