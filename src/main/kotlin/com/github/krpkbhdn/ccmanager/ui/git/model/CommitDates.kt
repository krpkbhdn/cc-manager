package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.ui.state.relativeTime
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val DAYS_PER_WEEK = 7L
private const val DAYS_PER_MONTH = 30L
private const val DAYS_PER_YEAR = 365L
private const val WEEKS_ONLY_UNTIL_DAYS = 28L
private val absoluteFormat = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

/** "3m ago", "5d ago", "3w ago", "7mo ago", "2y ago": compact age of a commit. */
fun commitAge(at: Instant, now: Instant): String {
    val days = Duration.between(at, now).toDays()
    return when {
        days < DAYS_PER_WEEK -> relativeTime(at, now)
        days < WEEKS_ONLY_UNTIL_DAYS -> "${days / DAYS_PER_WEEK}w ago"
        days < DAYS_PER_YEAR -> "${days / DAYS_PER_MONTH}mo ago"
        else -> "${days / DAYS_PER_YEAR}y ago"
    }
}

/** Local wall-clock "2026-10-24 14:03", for tooltips. */
fun absoluteDate(at: Instant, zone: ZoneId = ZoneId.systemDefault()): String = absoluteFormat.format(at.atZone(zone))
