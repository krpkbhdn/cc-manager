package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.RateLimitSnapshot
import com.github.krpkbhdn.ccmanager.usage.RateLimitWindow
import java.time.Duration
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

private const val HIGH_PERCENT = 70.0
private const val CRITICAL_PERCENT = 90.0
private const val MAX_PERCENT = 100.0
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L

enum class UsageLevel { NORMAL, HIGH, CRITICAL }

/** One usage window ready to draw: [fraction] 0..1 fills the bar, [tooltip] carries the exact numbers. */
data class UsageMeter(val label: String, val percentText: String, val fraction: Float, val level: UsageLevel, val tooltip: String)

fun usageLevel(percent: Double): UsageLevel = when {
    percent >= CRITICAL_PERCENT -> UsageLevel.CRITICAL
    percent >= HIGH_PERCENT -> UsageLevel.HIGH
    else -> UsageLevel.NORMAL
}

/** "resets in 3d 4h", "resets in 2h 14m", "resets in 9m"; "resets now" once the moment has passed. */
fun resetsInText(resetsAt: Instant, now: Instant): String {
    val minutes = Duration.between(now, resetsAt).toMinutes()
    val hours = minutes / MINUTES_PER_HOUR
    val days = hours / HOURS_PER_DAY
    if (minutes <= 0) return "resets now"
    return "resets in " + when {
        hours < 1 -> "${minutes}m"
        days < 1 -> "${hours}h ${minutes % MINUTES_PER_HOUR}m"
        else -> "${days}d ${hours % HOURS_PER_DAY}h"
    }
}

/** The windows still counting at [now]; one that already reset would show stale usage until the next report. */
fun usageMeters(snapshot: RateLimitSnapshot?, now: Instant): List<UsageMeter> = listOfNotNull(
    snapshot?.fiveHour?.let { meter("5h", "5-hour limit", it, now) },
    snapshot?.sevenDay?.let { meter("7d", "7-day limit", it, now) },
)

private fun meter(label: String, name: String, window: RateLimitWindow, now: Instant): UsageMeter? {
    if (!window.resetsAt().isAfter(now)) return null
    val percent = window.usedPercent().coerceIn(0.0, MAX_PERCENT)
    val exact = if (percent % 1.0 == 0.0) percent.roundToInt().toString() else "%.1f".format(Locale.ROOT, percent)
    return UsageMeter(label, "${percent.roundToInt()}%", (percent / MAX_PERCENT).toFloat(), usageLevel(percent), "$name: $exact% used, ${resetsInText(window.resetsAt(), now)}")
}
