package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitStatus

private const val SHORT_HEAD = 7
private const val DETACHED_LABEL = "detached"

/** The compact line shown in the project header and sidebar: where HEAD is, how dirty, how far from upstream. */
data class GitSummary(val branch: String, val dirty: Int, val ahead: Int, val behind: Int, val operation: GitStatus.Operation)

fun summaryOf(status: GitStatus): GitSummary? {
    if (!status.repository()) return null
    val name = status.branch() ?: "$DETACHED_LABEL ${status.head().orEmpty().take(SHORT_HEAD)}".trim()
    return GitSummary(name, changeSections(status.changes()).dirtyCount, status.ahead(), status.behind(), status.operation())
}

/** "main  3  ↑1 ↓2": branch plus the non-zero counters, for one-line places and tooltips. */
fun summaryText(summary: GitSummary): String = buildString {
    append(summary.branch)
    if (summary.dirty > 0) append("  ${summary.dirty} changed")
    if (summary.ahead > 0) append("  ↑${summary.ahead}")
    if (summary.behind > 0) append("  ↓${summary.behind}")
}
