package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.workspace.GitDiff

/** One rendered diff line; [highlight] marks the changed middle of a paired removed/added line. */
data class DiffRow(val line: GitDiff.Line, val highlight: IntRange? = null)

/** Range of [text] that differs from [other] once the shared prefix and suffix are removed; null when nothing differs. */
fun changedMiddle(text: String, other: String): IntRange? {
    var prefix = 0
    while (prefix < text.length && prefix < other.length && text[prefix] == other[prefix]) prefix++
    var suffix = 0
    while (suffix < text.length - prefix && suffix < other.length - prefix && text[text.length - 1 - suffix] == other[other.length - 1 - suffix]) suffix++
    val end = text.length - suffix
    return if (end > prefix) prefix until end else null
}

/** Pairs each run of removed lines with the added run right after it and marks the changed middle on both sides. */
fun diffRows(lines: List<GitDiff.Line>): List<DiffRow> {
    val rows = lines.map { DiffRow(it) }.toMutableList()
    var index = 0
    while (index < lines.size) {
        if (lines[index].kind != GitDiff.LineKind.REMOVED) { index++; continue }
        val removedEnd = runEnd(lines, index, GitDiff.LineKind.REMOVED)
        val addedEnd = runEnd(lines, removedEnd, GitDiff.LineKind.ADDED)
        val pairs = minOf(removedEnd - index, addedEnd - removedEnd)
        for (offset in 0 until pairs) {
            val removed = lines[index + offset]
            val added = lines[removedEnd + offset]
            rows[index + offset] = DiffRow(removed, changedMiddle(removed.text, added.text))
            rows[removedEnd + offset] = DiffRow(added, changedMiddle(added.text, removed.text))
        }
        index = addedEnd
    }
    return rows
}

private fun runEnd(lines: List<GitDiff.Line>, from: Int, kind: GitDiff.LineKind): Int {
    var end = from
    while (end < lines.size && lines[end].kind == kind) end++
    return end
}

private const val BAR_SEGMENTS = 5

/** How many of [BAR_SEGMENTS] blocks of the +/- bar are additions, deletions and the neutral remainder. */
fun changeBar(additions: Int, deletions: Int): Triple<Int, Int, Int> {
    val total = additions + deletions
    if (total == 0) return Triple(0, 0, BAR_SEGMENTS)
    var added = Math.round(BAR_SEGMENTS * additions.toFloat() / total)
    if (additions > 0 && added == 0) added = 1
    if (deletions > 0 && added == BAR_SEGMENTS) added = BAR_SEGMENTS - 1
    return Triple(added, BAR_SEGMENTS - added, 0)
}
