package com.github.krpkbhdn.ccmanager.ui.usage

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import com.github.krpkbhdn.ccmanager.usage.SessionUsageService
import com.github.krpkbhdn.ccmanager.usage.history.UsageHistoryRepository
import java.time.LocalDate
import kotlinx.coroutines.CoroutineScope

/**
 * Cost history of the chosen period. Reads run off the UI thread; the reload on every statusLine report happens only
 * while the screen is [visible]. A null [history] (previews) leaves [data] empty.
 */
class UsageState(
    private val history: UsageHistoryRepository?,
    private val usage: SessionUsageService,
    scope: CoroutineScope,
    private val visible: () -> Boolean,
    private val reportError: (String) -> Unit,
) {
    var period by mutableStateOf(UsagePeriod.MONTH)
        private set
    var data by mutableStateOf<UsageData?>(null)
        private set

    private val reload = Refresher(scope) { runGuarded(reportError) { load() } }
    private var unsubscribe: Runnable? = null

    fun start() {
        unsubscribe = usage.subscribe { if (visible()) reload.request() }
    }

    fun dispose() {
        unsubscribe?.run()
        reload.close()
    }

    fun refresh() = reload.request()

    fun select(chosen: UsagePeriod) {
        period = chosen
        reload.request()
    }

    private suspend fun load() {
        val repository = history ?: return
        val chosen = period
        val range = rangeOf(chosen, LocalDate.now())
        data = io {
            buildUsage(chosen, range, repository.dailyCosts(range.from, range.to), repository.summary(range.from, range.to), repository.topSessions(range.from, range.to, TOP_SESSIONS))
        }
    }
}
