package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.author.AuthorCatalogService
import com.github.krpkbhdn.ccmanager.author.AuthorInstallReport
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpState
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** What an install left for the user: report lines and MCP servers to sign in to. */
class AuthorReport(val lines: List<String>, val signIns: List<AuthorSignIn>)

class AuthorSignIn(val server: String, val mcp: McpState)

/**
 * The author's bundled user-level catalog in the UI: the dialog offered at start (new and updated items preselected) and opened on demand.
 * Work runs off the UI thread; closing or installing never records a decline, only "Don't offer these" does.
 */
class AuthorProfileState(
    private val service: AuthorCatalogService,
    private val scope: CoroutineScope,
    private val mcp: McpState,
    private val reportError: (String) -> Unit,
    private val toast: (NotificationKind, String, String) -> Unit,
) {
    var catalog by mutableStateOf<CatalogView?>(null)
        private set
    var report by mutableStateOf<AuthorReport?>(null)
        private set
    var busy by mutableStateOf(false)
        private set

    var startOfferPending = false
        private set

    val dialogOpen get() = catalog != null || report != null

    fun offerAtStart() {
        startOfferPending = true
        launchGuarded {
            try {
                showStartOffer()
            } finally {
                startOfferPending = false
            }
        }
    }

    fun openManage() = launchGuarded {
        val all = io { service.catalog() to service.entries() }
        catalog = CatalogView(all.first, all.second, offer = false)
    }

    fun install() {
        val view = catalog ?: return
        val ids = view.selected.toList()
        if (busy || ids.isEmpty()) return
        busy = true
        scope.launch {
            try {
                runGuarded(reportError) {
                    perform(ids)
                    reloadView(view)
                    view.selected = emptySet()
                    if (view.offer) catalog = null
                }
            } finally {
                busy = false
            }
        }
    }

    /** Records the offered items that are not checked as declined, so they are not offered again unprompted. */
    fun declineUnchecked() {
        val view = catalog ?: return
        val declined = CatalogSelection.toDecline(view.entries, view.selected).map { it.item().id() }
        if (declined.isEmpty()) return
        launchGuarded {
            io { service.decline(declined) }
            reloadView(view)
            if (view.offer && view.entries.none { it.status().offered() }) catalog = null
        }
    }

    fun closeCatalog() {
        if (!busy) catalog = null
    }

    fun closeReport() {
        report = null
    }

    private suspend fun showStartOffer() {
        val (items, entries) = io { service.catalog() to service.entries() }
        val wanted = entries.filter { it.status().offered() }
        if (wanted.isEmpty() || dialogOpen) return
        val view = CatalogView(items, entries, offer = true)
        view.selected = wanted.filterNot(::replacesEdits).map { it.item().id() }.toSet()
        catalog = view
    }

    private suspend fun reloadView(view: CatalogView) {
        view.reload(io { service.entries() })
    }

    private suspend fun perform(requested: List<String>) {
        val items = io { service.catalog() }
        val ids = requested.filter { items.byId(it).isPresent }
        if (ids.isEmpty()) return
        showResult(io { service.install(ids) })
    }

    private fun showResult(result: AuthorInstallReport) {
        val signIns = result.outcomes().flatMap { outcome -> outcome.outcome().signInNeeded().map { AuthorSignIn(it, mcp) } }
        if (result.outcomes().any { it.failed() } || signIns.isNotEmpty()) {
            report = AuthorReport(result.outcomes().flatMap { it.outcome().report() }, signIns)
        } else {
            toast(NotificationKind.INFO, "Author configuration installed", "Done")
        }
    }

    private fun launchGuarded(block: suspend () -> Unit) {
        scope.launch { runGuarded(reportError, block) }
    }
}
