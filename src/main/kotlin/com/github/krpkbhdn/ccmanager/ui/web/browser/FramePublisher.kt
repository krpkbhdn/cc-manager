package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.slf4j.LoggerFactory
import java.nio.ByteBuffer
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

/**
 * Publishes at most one frame per [intervalNanos]: paints in between are only staged and the newest one goes out
 * on a trailing flush, so the last paint of a burst is never lost. While hidden nothing is published.
 */
class FramePublisher(
    private val frames: FrameBuffer,
    private val sink: (BrowserFrame) -> Unit,
    private val scheduler: ScheduledExecutorService,
    private val intervalNanos: Long = DEFAULT_INTERVAL_NANOS,
    private val now: () -> Long = System::nanoTime,
) {
    private var lastPublished = Long.MIN_VALUE / 2
    private var flushScheduled = false
    @Volatile var hidden = false
    private var statsStart = now()
    private var paints = 0
    private var published = 0
    private var flushNanos = 0L

    @Synchronized
    fun onPaint(buffer: ByteBuffer, width: Int, height: Int, density: Double = 1.0) {
        if (hidden) return
        paints++
        frames.stage(buffer, width, height, density)
        if (flushScheduled) return
        val wait = lastPublished + intervalNanos - now()
        if (wait <= 0) publish() else schedule(wait)
    }

    /** Stops publishing for good and releases the images. */
    @Synchronized
    fun close() {
        hidden = true
        frames.close()
    }

    private fun schedule(waitNanos: Long) {
        flushScheduled = true
        scheduler.schedule({ flushPending() }, waitNanos, TimeUnit.NANOSECONDS)
    }

    @Synchronized
    private fun flushPending() {
        flushScheduled = false
        if (!hidden) publish()
    }

    private fun publish() {
        val started = now()
        val frame = frames.flush() ?: return
        lastPublished = now()
        flushNanos += lastPublished - started
        published++
        sink(frame)
        logStats(frame)
    }

    private fun logStats(frame: BrowserFrame) {
        val elapsed = lastPublished - statsStart
        if (elapsed < STATS_INTERVAL_NANOS) return
        if (log.isDebugEnabled) {
            val seconds = elapsed / NANOS_PER_SECOND
            log.debug(
                "Web frames: {} paints/s, {} published/s, avg flush {} ms, {}x{} px",
                "%.1f".format(paints / seconds), "%.1f".format(published / seconds),
                "%.2f".format(flushNanos / published / 1e6), frame.widthPx, frame.heightPx,
            )
        }
        statsStart = lastPublished
        paints = 0
        published = 0
        flushNanos = 0
    }

    companion object {
        const val DEFAULT_INTERVAL_NANOS = 16_000_000L
        private const val STATS_INTERVAL_NANOS = 5_000_000_000L
        private const val NANOS_PER_SECOND = 1e9
        private val log = LoggerFactory.getLogger(FramePublisher::class.java)
    }
}
