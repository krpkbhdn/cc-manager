package com.github.krpkbhdn.ccmanager.ui

private const val MIN_RECOVERY_INTERVAL_MILLIS = 10_000L

/** Allows a window remount after a UI exception at most once per interval, so a bug that fails on every composition cannot loop forever. */
class WindowRecovery(private val clock: () -> Long = System::currentTimeMillis, private val minIntervalMillis: Long = MIN_RECOVERY_INTERVAL_MILLIS) {
    private var lastRecoveryAt: Long? = null

    fun tryRecover(): Boolean {
        val now = clock()
        val last = lastRecoveryAt
        if (last != null && now - last < minIntervalMillis) return false
        lastRecoveryAt = now
        return true
    }
}
