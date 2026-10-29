package com.github.krpkbhdn.ccmanager.ui.voice

import java.util.regex.Pattern

private const val TAIL_LIMIT = 128
private const val ELLIPSIS = "..."
private val WHITESPACE = Pattern.compile("\\s+", Pattern.UNICODE_CHARACTER_CLASS).toRegex()

/** The end of a growing transcript that fits a small preview: whitespace (Unicode ones too) collapsed, long text cut to its last [limit] chars behind "..."; a limit of 0 or less keeps only the "...". */
object LiveTranscriptTail {
    fun of(text: String, limit: Int = TAIL_LIMIT): String {
        val normalized = text.trim().replace(WHITESPACE, " ")
        if (normalized.length <= limit || normalized.isEmpty()) return normalized
        var start = normalized.length - limit.coerceAtLeast(0)
        if (start < normalized.length && normalized[start].isLowSurrogate()) start++
        return ELLIPSIS + normalized.substring(start)
    }
}
