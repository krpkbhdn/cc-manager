package com.github.krpkbhdn.ccmanager.ui.web.browser

import java.util.concurrent.TimeUnit

/** Allows at most [limit] calls per one-second window; a page flooding the host must not push out genuine messages. */
class PerSecondLimit(private val limit: Int, private val now: () -> Long = System::nanoTime) {
    private var windowStart = Long.MIN_VALUE / 2
    private var used = 0

    @Synchronized
    fun tryAcquire(): Boolean {
        val time = now()
        if (time - windowStart >= TimeUnit.SECONDS.toNanos(1)) {
            windowStart = time
            used = 0
        }
        return used++ < limit
    }
}
