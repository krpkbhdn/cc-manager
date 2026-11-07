package com.github.krpkbhdn.ccmanager.ui.git.changes

import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewComment
import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewStatus
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

/** Review comments of the shown file and what adding one does; [onAdd] gets the selected diff row indexes and the text. */
class ReviewEditing(
    val comments: List<ReviewComment>,
    val onAdd: (IntRange, String) -> Unit,
)

/** The new range after a click on row [clicked]: a Shift click grows the range inside its hunk, an unselectable row changes nothing. */
fun extendSelection(current: IntRange?, clicked: Int, extend: Boolean, hunks: List<Hunk>, selectable: (Int) -> Boolean): IntRange? {
    if (!selectable(clicked)) return current
    val hunk = hunks.firstOrNull { clicked in it.body } ?: return current
    if (!extend || current == null || current.first !in hunk.body) return clicked..clicked
    return minOf(current.first, clicked)..maxOf(current.last, clicked)
}

/** The status a line's gutter shows: STALE wins so a comment that lost its place is never hidden by an open one. */
internal fun displayStatus(covering: List<ReviewComment>): ReviewStatus? =
    covering.firstOrNull { it.status == ReviewStatus.STALE }?.status ?: covering.firstOrNull()?.status

internal fun ReviewComment.covers(line: GitDiff.Line): Boolean {
    val number = if (removedOnly) line.oldNumber.takeIf { line.kind == GitDiff.LineKind.REMOVED } else line.newNumber.takeIf { line.kind != GitDiff.LineKind.REMOVED }
    return number != null && number in lines
}
