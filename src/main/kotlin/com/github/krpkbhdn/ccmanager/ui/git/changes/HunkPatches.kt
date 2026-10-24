package com.github.krpkbhdn.ccmanager.ui.git.changes

import com.github.krpkbhdn.ccmanager.workspace.GitDiff

/** One hunk of a file diff: [header] is the index of its HUNK line in [GitDiff.FileDiff.lines], [body] the indexes after it. */
data class Hunk(val number: Int, val header: Int, val body: IntRange)

private val hunkHeader = Regex("^@@ -(\\d+)(?:,(\\d+))? \\+(\\d+)(?:,(\\d+))? @@(.*)$")

fun hunksOf(file: GitDiff.FileDiff): List<Hunk> {
    val headers = file.lines.indices.filter { file.lines[it].kind == GitDiff.LineKind.HUNK }
    return headers.mapIndexed { number, header ->
        val end = headers.getOrNull(number + 1)?.minus(1) ?: file.lines.lastIndex
        Hunk(number, header, header + 1..end)
    }
}

/** True for lines a user can pick for partial staging. */
fun isChangeLine(line: GitDiff.Line): Boolean = line.kind == GitDiff.LineKind.ADDED || line.kind == GitDiff.LineKind.REMOVED

/** Indexes of the changed lines of [hunk]. */
fun changeIndexes(file: GitDiff.FileDiff, hunk: Hunk): List<Int> = hunk.body.filter { index -> index < file.lines.size && isChangeLine(file.lines[index]) }

/** Patch applying exactly [hunk]; feed it to `applyToIndex` (reverse = true to unstage). */
fun hunkPatch(file: GitDiff.FileDiff, hunk: Hunk): String = linePatch(file, hunk, changeIndexes(file, hunk).toSet(), reverse = false)

/**
 * Patch applying only the [selected] changed lines of [hunk]. For a forward apply an unselected removal stays as
 * context and an unselected addition is dropped; for a [reverse] apply (unstaging) it is the other way round.
 */
fun linePatch(file: GitDiff.FileDiff, hunk: Hunk, selected: Set<Int>, reverse: Boolean): String {
    val header = hunkHeader.matchEntire(file.lines[hunk.header].text) ?: error("Not a hunk header: ${file.lines[hunk.header].text}")
    val body = mutableListOf<String>()
    var oldCount = 0
    var newCount = 0
    var previousKept = true
    for (index in hunk.body) {
        val line = file.lines[index]
        val picked = index in selected
        val emitted = when (line.kind) {
            GitDiff.LineKind.CONTEXT -> " ${line.text}".also { oldCount++; newCount++ }
            GitDiff.LineKind.REMOVED -> if (picked) "-${line.text}".also { oldCount++ } else if (!reverse) " ${line.text}".also { oldCount++; newCount++ } else null
            GitDiff.LineKind.ADDED -> if (picked) "+${line.text}".also { newCount++ } else if (reverse) " ${line.text}".also { oldCount++; newCount++ } else null
            else -> if (previousKept) line.text else null
        }
        if (line.kind != GitDiff.LineKind.META) previousKept = emitted != null
        emitted?.let(body::add)
    }
    val oldStart = header.groupValues[1]
    val newStart = if (selected.size == changeIndexes(file, hunk).size) header.groupValues[3] else oldStart
    val section = header.groupValues[5]
    val lines = listOf(fileHeader(file), "@@ -$oldStart,$oldCount +$newStart,$newCount @@$section") + body
    return lines.joinToString("\n", postfix = "\n")
}

private fun fileHeader(file: GitDiff.FileDiff): String {
    val oldPath = file.oldPath ?: file.path
    val from = if (file.change == GitDiff.ChangeType.ADDED || file.change == GitDiff.ChangeType.UNTRACKED) "/dev/null" else "a/$oldPath"
    val to = if (file.change == GitDiff.ChangeType.DELETED) "/dev/null" else "b/${file.path}"
    val mode = when (file.change) {
        GitDiff.ChangeType.ADDED, GitDiff.ChangeType.UNTRACKED -> "new file mode 100644\n"
        GitDiff.ChangeType.DELETED -> "deleted file mode 100644\n"
        else -> ""
    }
    return "diff --git a/$oldPath b/${file.path}\n$mode--- $from\n+++ $to"
}
