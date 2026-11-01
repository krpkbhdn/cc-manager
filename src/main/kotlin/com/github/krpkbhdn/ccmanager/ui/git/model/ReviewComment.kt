package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.ui.state.DiffRow
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind
import java.util.UUID

enum class ReviewStatus { OPEN, SENT, STALE }

/**
 * A user's note on diff lines. [lines] are new-side numbers (old-side when [removedOnly]); [anchor] is the text used
 * to find the lines again after the file changed.
 */
data class ReviewComment(
    val id: String,
    val path: String,
    val lines: IntRange,
    val removedOnly: Boolean,
    val snippet: List<String>,
    val anchor: List<String>,
    val text: String,
    val status: ReviewStatus = ReviewStatus.OPEN,
)

private val markers = mapOf(LineKind.ADDED to "+", LineKind.REMOVED to "-", LineKind.CONTEXT to " ")

/** Builds a comment over the diff rows at the [range] of row indexes; non-code rows (hunk headers, meta) are ignored. */
fun reviewCommentOf(path: String, rows: List<DiffRow>, range: IntRange, text: String): ReviewComment {
    val picked = range.mapNotNull { rows.getOrNull(it)?.line }.filter { it.kind in markers }
    val current = picked.filter { it.kind != LineKind.REMOVED }
    val removedOnly = current.isEmpty()
    val numbers = if (removedOnly) picked.mapNotNull { it.oldNumber } else current.mapNotNull { it.newNumber }
    return ReviewComment(
        id = UUID.randomUUID().toString(),
        path = path,
        lines = (numbers.minOrNull() ?: 0)..(numbers.maxOrNull() ?: 0),
        removedOnly = removedOnly,
        snippet = picked.map { markers.getValue(it.kind) + it.text },
        anchor = (if (removedOnly) picked else current).map { it.text },
        text = text,
    )
}
