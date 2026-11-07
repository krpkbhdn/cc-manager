package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.notification.AppNotification
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import java.time.Duration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory

private val log = LoggerFactory.getLogger(OsNotifier::class.java)
private val BATCH_WINDOW = Duration.ofMillis(1500)

/** Turns notifications into desktop popups: alerts arriving within [BATCH_WINDOW] become one summary. Used on the UI thread only. */
class OsNotifier(
    private val scope: CoroutineScope,
    private val settings: () -> UiSettings,
    private val windowActive: () -> Boolean,
    private val isWaiting: (String) -> Boolean,
    private val describe: (AppNotification) -> OsAlert,
    private val show: (OsNotice) -> Unit,
) {
    private val batch = mutableListOf<AppNotification>()
    private var flushing: Job? = null

    fun offer(notification: AppNotification) {
        if (!shouldNotify(settings(), notification.kind, windowActive())) {
            log.info("Desktop popup skipped: kind={} windowActive={}", notification.kind, windowActive())
            return
        }
        batch += notification
        if (flushing == null) flushing = scope.launch { delay(BATCH_WINDOW.toMillis()); flush() }
    }

    private fun flush() {
        try {
            val current = batch.filter(::stillRelevant)
            if (current.isEmpty()) {
                log.info("Desktop popup dropped at flush: no alert still relevant (queued={})", batch.size)
                return
            }
            show(summarize(current.map(describe)))
        } catch (e: Exception) {
            log.warn("Desktop popup failed to show", e)
        } finally {
            batch.clear()
            flushing = null
        }
    }

    private fun stillRelevant(notification: AppNotification): Boolean {
        val sessionId = notification.ccmSessionId
        val stillAsking = notification.kind != NotificationKind.NEEDS_ACTION || sessionId == null || isWaiting(sessionId)
        return stillAsking && shouldNotify(settings(), notification.kind, windowActive())
    }
}
