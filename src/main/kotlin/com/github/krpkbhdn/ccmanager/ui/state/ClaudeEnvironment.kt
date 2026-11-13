package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.ide.IdeIconExtractor
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import com.github.krpkbhdn.ccmanager.workspace.CustomIde
import com.github.krpkbhdn.ccmanager.workspace.InstalledIde
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the machine and the Claude account report: the installation, the IDE, account rate limits and per-session usage. */
class ClaudeEnvironment(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val prefs: UiPreferences,
    private val reportError: (String) -> Unit,
) {
    private val ideIconExtractor = IdeIconExtractor()
    var installation by mutableStateOf<ClaudeInstallation?>(null)
        private set
    var checkingInstallation by mutableStateOf(false)
        private set
    var ides by mutableStateOf<List<InstalledIde>>(emptyList())
        private set
    var customIdes by mutableStateOf<List<CustomIde>>(emptyList())
        private set
    var ideIcons by mutableStateOf<Map<String, ImageBitmap>>(emptyMap())
        private set
    var ideDialogOpen by mutableStateOf(false)
    var rateLimits by mutableStateOf<RateLimitSnapshot?>(null)
        private set
    var sessionUsage by mutableStateOf<Map<String, SessionUsage>>(emptyMap())
        private set

    val ideAvailable: Boolean get() = ides.isNotEmpty()
    val defaultIde: InstalledIde? get() = resolveDefaultIde(ides, prefs.settings.defaultIdeId)

    /** Loads everything once and returns the callbacks to unsubscribe; [onInstallation] gets the first installation read. */
    fun start(onInstallation: (ClaudeInstallation?) -> Unit): List<Runnable> {
        val unsubscribers = listOf(
            backend.rateLimits.subscribe { scope.launch { rateLimits = backend.rateLimits.current().orElse(null) } },
            backend.sessionUsage.subscribe { scope.launch { sessionUsage = backend.sessionUsage.all() } },
            backend.customIdes.subscribe { refreshIdes() },
        )
        scope.launch { rateLimits = backend.rateLimits.current().orElse(null) }
        scope.launch { sessionUsage = backend.sessionUsage.all() }
        scope.launch {
            runGuarded(reportError) {
                installation = io { backend.installation.current() }
                onInstallation(installation)
            }
        }
        refreshIdes()
        return unsubscribers
    }

    fun openFolder(project: Project) = openDirectory(project.path)

    fun openDirectory(directory: Path) = scope.launch { runGuarded(reportError) { io { backend.directories.open(directory) } } }

    fun openIde(project: Project) = openInIde(project.path)

    fun openInIde(target: Path) {
        val ide = defaultIde ?: return reportError("No IDE was found. Add one with \"Add IDE...\".")
        openInIde(ide.id, target)
    }

    fun openInIde(ideId: String, target: Path) = scope.launch { runGuarded(reportError) { io { backend.ide.open(ideId, target) } } }

    fun makeDefaultIde(ideId: String) = prefs.update { it.copy(defaultIdeId = ideId) }

    fun recheckIdes() = refreshIdes(detectAgain = true)

    private fun refreshIdes(detectAgain: Boolean = false) = scope.launch {
        runGuarded(reportError) {
            if (detectAgain) io { backend.ideDetector.recheck() }
            ides = io { backend.ide.list() }
            customIdes = io { backend.customIdes.list() }
            loadIdeIcons(ides)
        }
    }

    private suspend fun loadIdeIcons(listed: List<InstalledIde>) {
        val missing = listed.filter { it.id !in ideIcons }
        val loaded = io { missing.mapNotNull { ide -> ideIconExtractor.extract(ide)?.let { ide.id to it } }.toMap() }
        ideIcons = ideIcons + loaded
    }

    /** Saves a custom IDE; a rejected value comes back as readable text in [onError]. */
    fun addCustomIde(name: String, executable: String, arguments: String, onError: (String) -> Unit, onDone: () -> Unit) = scope.launch {
        try {
            io { backend.customIdes.create(name, executable, arguments) }
            onDone()
        } catch (e: IllegalArgumentException) {
            onError(e.message ?: "The IDE could not be saved")
        }
    }

    fun deleteCustomIde(id: Long) = scope.launch { runGuarded(reportError) { io { backend.customIdes.delete(id) } } }

    fun recheckInstallation() = scope.launch {
        checkingInstallation = true
        runGuarded(reportError) { installation = io { backend.installation.recheck() } }
        checkingInstallation = false
    }
}
