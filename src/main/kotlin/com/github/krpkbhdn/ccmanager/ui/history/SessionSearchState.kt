package com.github.krpkbhdn.ccmanager.ui.history

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService
import com.github.krpkbhdn.ccmanager.history.SessionSearchService
import com.github.krpkbhdn.ccmanager.history.SessionSearchService.SearchHit
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import java.time.Instant
import java.util.Optional
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch

private const val DEBOUNCE_MILLIS = 250L
private const val COUNTER_REFRESH_MILLIS = 15_000L

/** A transcript opened at a search hit; [window] is null while it loads. */
class OpenedTranscript(val hit: SearchHit, val window: TranscriptService.Window?)

/** Search over the indexed Claude transcripts: the query and filters are debounced, the search runs off the UI thread. */
class SessionSearchState(
    private val service: SessionSearchService,
    private val transcripts: TranscriptService,
    private val scope: CoroutineScope,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val reportError: (String) -> Unit,
    private val now: () -> Instant = Instant::now,
) {
    val query = TextFieldState()
    var project by mutableStateOf<Project?>(null)
    var period by mutableStateOf(SearchPeriod.ANY)
    var hits by mutableStateOf<List<SearchHit>>(emptyList())
        private set
    var searching by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var indexedEntries by mutableStateOf(0L)
        private set
    var opened by mutableStateOf<OpenedTranscript?>(null)
        private set

    private var job: Job? = null
    private var counterJob: Job? = null

    /** The entry counter is refreshed on a timer, so a search does not pay for a SUM over the index. */
    @OptIn(FlowPreview::class)
    fun start() {
        startCounter()
        job = scope.launch {
            snapshotFlow { Triple(query.text.toString().trim(), project, period) }.debounce(DEBOUNCE_MILLIS).collectLatest { (text, project, period) -> search(text, project, period) }
        }
    }

    private fun startCounter() {
        counterJob = scope.launch {
            while (true) {
                refreshIndexedEntries()
                delay(COUNTER_REFRESH_MILLIS)
            }
        }
    }

    fun dispose() {
        counterJob?.cancel()
        job?.cancel()
    }

    private suspend fun search(text: String, project: Project?, period: SearchPeriod) {
        error = null
        if (text.isEmpty()) {
            hits = emptyList()
            return
        }
        searching = true
        try {
            runGuarded({ error = it }) {
                hits = io { service.search(text, Optional.ofNullable(project?.path?.toString()), Optional.ofNullable(period.from(now())), Optional.empty()) }
            }
        } finally {
            searching = false
        }
    }

    private suspend fun refreshIndexedEntries() = runGuarded(reportError) { indexedEntries = io { service.indexedEntryCount() } }

    fun open(hit: SearchHit) {
        opened = OpenedTranscript(hit, null)
        scope.launch {
            runGuarded({ opened = null; reportError(it) }) {
                val window = io { transcripts.readWindow(hit.path, hit.lineOffset) }
                if (opened?.hit === hit) opened = OpenedTranscript(hit, window)
            }
        }
    }

    fun closeTranscript() {
        opened = null
    }

    fun requestClear() {
        val target = project ?: return
        requestConfirm(
            ConfirmRequest(
                title = "Clear session history",
                message = "Forget the indexed history of ${target.name}? The transcripts stay on disk, but they are not indexed again, so they will not be found by search.",
                confirmLabel = "Clear",
            ) { clear(target) },
        )
    }

    private fun clear(target: Project) {
        scope.launch {
            runGuarded(reportError) {
                io { service.clear(target.path.toString()) }
                hits = emptyList()
                refreshIndexedEntries()
            }
        }
    }
}
