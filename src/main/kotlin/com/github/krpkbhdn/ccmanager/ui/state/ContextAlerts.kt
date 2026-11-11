package com.github.krpkbhdn.ccmanager.ui.state

import androidx.compose.runtime.mutableStateSetOf
import com.github.krpkbhdn.ccmanager.usage.SessionUsage

/** Sessions whose "compact?" marker the user closed; a session leaves the set once its context is no longer critical, so the next fill-up alerts again. */
class ContextAlerts {
    private val dismissed = mutableStateSetOf<String>()

    fun shouldAlert(ccmSessionId: String, view: ContextUsageView?): Boolean = view?.level == UsageLevel.CRITICAL && ccmSessionId !in dismissed

    fun dismiss(ccmSessionId: String) {
        dismissed.add(ccmSessionId)
    }

    fun rearmBelowCritical(usage: Map<String, SessionUsage>) {
        dismissed.removeAll { contextUsageView(usage[it])?.level != UsageLevel.CRITICAL }
    }
}
