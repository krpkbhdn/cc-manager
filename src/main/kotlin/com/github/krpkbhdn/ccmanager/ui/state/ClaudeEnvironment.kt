package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What the machine and the Claude account report: the installation, the IDE, account rate limits and per-session usage. */
class ClaudeEnvironment(
    private val backend: Backend,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    var installation by mutableStateOf<ClaudeInstallation?>(null)
        private set
    var checkingInstallation by mutableStateOf(false)
        private set
    var ideAvailable by mutableStateOf(false)
        private set
    var rateLimits by mutableStateOf<RateLimitSnapshot?>(null)
        private set
    var sessionUsage by mutableStateOf<Map<String, SessionUsage>>(emptyMap())
        private set

    /** Loads everything once and returns the callbacks to unsubscribe; [onInstallation] gets the first installation read. */
    fun start(onInstallation: (ClaudeInstallation?) -> Unit): List<Runnable> {
        val unsubscribers = listOf(
            backend.rateLimits.subscribe { scope.launch { rateLimits = backend.rateLimits.current().orElse(null) } },
            backend.sessionUsage.subscribe { scope.launch { sessionUsage = backend.sessionUsage.all() } },
        )
        scope.launch { rateLimits = backend.rateLimits.current().orElse(null) }
        scope.launch { sessionUsage = backend.sessionUsage.all() }
        scope.launch {
            runGuarded(reportError) {
                installation = io { backend.installation.current() }
                onInstallation(installation)
            }
        }
        scope.launch { runGuarded(reportError) { ideAvailable = io { backend.ide.available() } } }
        return unsubscribers
    }

    fun openFolder(project: Project) = openDirectory(project.path)

    fun openDirectory(directory: Path) = scope.launch { runGuarded(reportError) { io { backend.directories.open(directory) } } }

    fun openIde(project: Project) = openInIde(project.path)

    fun openInIde(target: Path) = scope.launch { runGuarded(reportError) { io { backend.ide.open(target) } } }

    fun recheckInstallation() = scope.launch {
        checkingInstallation = true
        runGuarded(reportError) { installation = io { backend.installation.recheck() } }
        checkingInstallation = false
    }
}
