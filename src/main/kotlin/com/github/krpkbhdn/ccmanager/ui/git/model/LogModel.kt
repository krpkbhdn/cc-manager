package com.github.krpkbhdn.ccmanager.ui.git.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.git.GitException
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.git.GitLogService
import com.github.krpkbhdn.ccmanager.git.GraphState
import com.github.krpkbhdn.ccmanager.ui.git.log.LogFilter
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Paged commit history with filter and the selected commit's details; stale responses are dropped by [generation]. */
class LogModel(
    private val repo: () -> Path,
    private val git: GitBackend,
    private val scope: CoroutineScope,
    private val onError: (GitException) -> Unit,
) {
    var rows by mutableStateOf<List<GitLog.Row>>(emptyList())
        private set
    var hasMore by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var filter by mutableStateOf(LogFilter())
        private set
    var selected by mutableStateOf<String?>(null)
        private set
    var details by mutableStateOf<GitLog.Details?>(null)
        private set
    var selectedFile by mutableStateOf<String?>(null)

    var loaded = false
        private set
    private var generation = 0
    private var graphState: GraphState? = null

    fun reload() {
        loaded = true
        val ticket = ++generation
        loading = true
        scope.launch {
            val result = fetch(0, null) ?: return@launch finish(ticket)
            if (ticket == generation) {
                val page = result.page()
                graphState = result.next()
                rows = page.rows()
                hasMore = page.hasMore()
                if (selected != null && rows.none { it.commit().hash() == selected }) clearSelection()
            }
            finish(ticket)
        }
    }

    fun loadMore() {
        if (loading || !hasMore) return
        val ticket = generation
        loading = true
        scope.launch {
            val result = fetch(rows.size, graphState)
            if (result != null && ticket == generation) {
                val page = result.page()
                graphState = result.next()
                val shown = rows.mapTo(HashSet()) { it.commit().hash() }
                rows = rows + page.rows().filter { it.commit().hash() !in shown }
                hasMore = page.hasMore()
            }
            finish(ticket)
        }
    }

    fun update(change: (LogFilter) -> LogFilter) {
        val next = change(filter)
        if (next == filter) return
        filter = next
        reload()
    }

    fun select(hash: String?) {
        if (hash == null) return clearSelection()
        selected = hash
        selectedFile = null
        scope.launch {
            val loadedDetails = gitCall(onError) { git.log.details(repo(), hash) }
            if (selected == hash) {
                details = loadedDetails
                selectedFile = loadedDetails?.files()?.firstOrNull()?.path
            }
        }
    }

    private fun clearSelection() {
        selected = null
        details = null
        selectedFile = null
    }

    private suspend fun fetch(skip: Int, previous: GraphState?): GitLogService.StatefulPage? =
        gitCall(onError) { git.log.log(repo(), filter.toQuery(skip), previous) }

    private fun finish(ticket: Int) {
        if (ticket == generation) loading = false
    }
}
