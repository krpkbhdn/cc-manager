package com.github.krpkbhdn.ccmanager.ui.voice

/** Text after a dictation step, where the caret goes, and the span that was dictated in this hold. */
internal class DictationApplied(val text: String, val caret: Int, val dictated: IntRange, val replaceFrom: Int, val replaceTo: Int, val inserted: String)

/**
 * Pure text editing of one hold: the live segment (the utterance still being recognised) is replaced in place, a final utterance
 * is committed and the next one starts after it. Spaces are added only where a neighbour is not already whitespace.
 */
internal class DictationEdit(private val origin: Int) {
    private var liveStart = origin
    private var liveEnd = origin

    fun apply(text: String, utterance: String, commit: Boolean): DictationApplied {
        val piece = utterance.trim()
        val start = liveStart.coerceAtMost(text.length)
        val before = text.substring(0, start)
        val end = liveEnd.coerceIn(start, text.length)
        val after = text.substring(end)
        val lead = if (piece.isNotEmpty() && before.isNotEmpty() && !before.last().isWhitespace()) " " else ""
        val trail = if (piece.isNotEmpty() && after.isNotEmpty() && !after.first().isWhitespace()) " " else ""
        val caret = start + lead.length + piece.length
        val inserted = lead + piece + trail
        val result = DictationApplied(before + inserted + after, caret, origin until caret, start, end, inserted)
        liveStart = start
        liveEnd = start + inserted.length
        if (commit && piece.isNotEmpty()) {
            liveStart = caret
            liveEnd = caret
        }
        return result
    }

    companion object {
        /** How many of the [wanted] spaces a held Space typed are really right before [caret]. */
        fun spacesBefore(text: String, caret: Int, wanted: Int): Int = text.substring(0, caret).takeLastWhile { it == ' ' }.length.coerceAtMost(wanted)
    }
}
