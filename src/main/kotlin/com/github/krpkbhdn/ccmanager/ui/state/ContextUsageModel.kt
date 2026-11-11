package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.ContextWindow
import com.github.krpkbhdn.ccmanager.usage.SessionUsage
import java.time.Instant
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_PERCENT = 100.0
const val CONTEXT_HIGH_PERCENT = 50.0
const val CONTEXT_CRITICAL_PERCENT = 70.0
private const val THOUSAND = 1_000.0
private const val MILLION = 1_000_000.0

enum class ContextPart(val label: String) {
    CACHE_READ("Cache read"),
    CACHE_WRITE("Cache write"),
    INPUT("Input"),
    FREE("Free"),
}

/** One slice of the context window: [fraction] of the whole window, 0..1. */
data class ContextSegment(val part: ContextPart, val tokens: Long, val fraction: Float)

/** A session's context window ready to draw; [segments] is empty while Claude has sent only the percent. */
data class ContextUsageView(
    val percentText: String,
    val fraction: Float,
    val level: UsageLevel,
    val tokensText: String?,
    val segments: List<ContextSegment>,
    val outputTokens: Long?,
    val totalInputTokens: Long?,
    val totalOutputTokens: Long?,
    val model: String?,
    val receivedAt: Instant,
)

/** Context fills up faster than a rate limit matters, so it turns yellow and red earlier than [usageLevel]. */
fun contextLevel(percent: Double): UsageLevel = when {
    percent > CONTEXT_CRITICAL_PERCENT -> UsageLevel.CRITICAL
    percent > CONTEXT_HIGH_PERCENT -> UsageLevel.HIGH
    else -> UsageLevel.NORMAL
}

/** Null when the session reported neither a percent nor token counts. */
fun contextUsageView(usage: SessionUsage?): ContextUsageView? {
    usage ?: return null
    val context = usage.context()
    val used = context?.usedTokens()
    val window = context?.windowSize()?.takeIf { it > 0 }
    val percent = (usage.contextUsedPercent() ?: if (used != null && window != null) used * MAX_PERCENT / window else null)
        ?.coerceIn(0.0, MAX_PERCENT) ?: return null
    return ContextUsageView(
        percentText = exactPercent(percent),
        fraction = (percent / MAX_PERCENT).toFloat(),
        level = contextLevel(percent),
        tokensText = if (used != null && window != null) "${compactTokens(used)} / ${compactTokens(window)}" else null,
        segments = if (used != null && window != null) segments(context, used, window) else emptyList(),
        outputTokens = context?.outputTokens(),
        totalInputTokens = context?.totalInputTokens(),
        totalOutputTokens = context?.totalOutputTokens(),
        model = usage.modelName(),
        receivedAt = usage.receivedAt(),
    )
}

private fun segments(context: ContextWindow, used: Long, window: Long): List<ContextSegment> {
    fun segment(part: ContextPart, tokens: Long) = ContextSegment(part, tokens, (tokens.toDouble() / window).coerceIn(0.0, 1.0).toFloat())
    return listOf(
        segment(ContextPart.CACHE_READ, context.cacheReadTokens() ?: 0),
        segment(ContextPart.CACHE_WRITE, context.cacheCreationTokens() ?: 0),
        segment(ContextPart.INPUT, context.inputTokens() ?: 0),
        segment(ContextPart.FREE, (window - used).coerceAtLeast(0)),
    )
}

/** "999", "1.2k", "200k", "1.5M". */
fun compactTokens(tokens: Long): String = when {
    tokens < THOUSAND -> tokens.toString()
    tokens < MILLION -> scaled(tokens / THOUSAND, "k")
    else -> scaled(tokens / MILLION, "M")
}

private fun scaled(value: Double, suffix: String): String =
    (if (value.roundToInt().toDouble() == value) value.roundToInt().toString() else "%.1f".format(Locale.ROOT, value)) + suffix
