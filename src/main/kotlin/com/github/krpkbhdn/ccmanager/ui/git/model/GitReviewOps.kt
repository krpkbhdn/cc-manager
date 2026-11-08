package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Lines a comment anchor is searched in, with the 1-based file line number of each. */
class ReviewHaystack(val texts: List<String>, val numbers: List<Int>) {
    /** Where the anchor of [comment] sits now, nearest its current position; null when it is gone. */
    fun locate(comment: ReviewComment): IntRange? {
        val near = numbers.indexOfFirst { it >= comment.lines.first }.coerceAtLeast(0)
        return locate(comment.anchor, texts, near)?.let { numbers[it.first]..numbers[it.last] }
    }
}

/** The current content of [path] under [repo]; null when the file is gone or unreadable. */
fun fileHaystack(repo: Path, path: String): ReviewHaystack? {
    val file = repo.resolve(path)
    if (!Files.isRegularFile(file)) return null
    val lines = try {
        Files.readAllLines(file)
    } catch (e: java.io.IOException) {
        return null
    }
    return ReviewHaystack(lines, lines.indices.map { it + 1 })
}

/** The removed lines of the given diffs, numbered by their old-side line. */
fun removedHaystack(diffs: List<GitDiff.FileDiff?>): ReviewHaystack {
    val removed = diffs.filterNotNull().flatMap { it.lines() }.filter { it.kind() == LineKind.REMOVED && it.oldNumber() != null }
    return ReviewHaystack(removed.map { it.text() }, removed.map { it.oldNumber() })
}

/**
 * Finds the comments again in the changed files: OPEN and STALE ones move (or turn STALE) with [recheck] false, SENT ones are
 * checked after the agent's turn and become OPEN again when still found, STALE otherwise.
 */
fun GitState.reanchorReview(recheck: Boolean) {
    val wanted = if (recheck) setOf(ReviewStatus.SENT) else setOf(ReviewStatus.OPEN, ReviewStatus.STALE)
    val read = review.all.filter { it.status in wanted }.associate { it.id to it.status }
    if (read.isEmpty()) return
    val comments = review.all.filter { it.id in read }
    scope.launch {
        val results = comments.associate { it.id to relocate(it) }
        val unchanged = results.filterKeys { review.statusOf(it) == read[it] }
        if (recheck) review.recheck(unchanged) else unchanged.forEach { (id, range) -> review.relocated(id, range) }
    }
}

private suspend fun GitState.relocate(comment: ReviewComment): IntRange? =
    if (comment.removedOnly) {
        val diffs = listOf(false, true).map { diffOf(ChangeRef(comment.path, it)) }
        removedHaystack(diffs).locate(comment)
    } else {
        withContext(Dispatchers.IO) { fileHaystack(repo, comment.path) }?.locate(comment)
    }
