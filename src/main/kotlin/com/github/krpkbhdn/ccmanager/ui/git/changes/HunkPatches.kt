package com.github.krpkbhdn.ccmanager.ui.git.changes

import com.github.krpkbhdn.ccmanager.workspace.GitDiff

/** One hunk of a file diff: [header] is the index of its HUNK line in [GitDiff.FileDiff.lines], [body] the indexes after it. */
data class Hunk(val number: Int, val header: Int, val body: IntRange)

fun hunksOf(file: GitDiff.FileDiff): List<Hunk> {
    val headers = file.lines.indices.filter { file.lines[it].kind == GitDiff.LineKind.HUNK }
    return headers.mapIndexed { number, header ->
        val end = headers.getOrNull(number + 1)?.minus(1) ?: file.lines.lastIndex
        Hunk(number, header, header + 1..end)
    }
}

/** True for lines a user can pick for partial staging. */
fun isChangeLine(line: GitDiff.Line): Boolean = line.kind == GitDiff.LineKind.ADDED || line.kind == GitDiff.LineKind.REMOVED
