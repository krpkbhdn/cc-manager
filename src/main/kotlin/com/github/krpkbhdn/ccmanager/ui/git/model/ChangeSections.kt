package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitStatus

/** The Changes view's lists: a file with both staged and unstaged edits appears in both; conflicts only in their own. */
data class ChangeSections(
    val conflicts: List<GitStatus.Change>,
    val staged: List<GitStatus.Change>,
    val unstaged: List<GitStatus.Change>,
) {
    val isClean: Boolean get() = conflicts.isEmpty() && staged.isEmpty() && unstaged.isEmpty()
    val dirtyCount: Int get() = (conflicts + staged + unstaged).map { it.path }.toSet().size
}

fun changeSections(changes: List<GitStatus.Change>): ChangeSections {
    val byPath = changes.sortedBy { it.path }
    return ChangeSections(
        conflicts = byPath.filter { it.conflicted() },
        staged = byPath.filter { !it.conflicted() && it.hasStaged() },
        unstaged = byPath.filter { !it.conflicted() && it.hasUnstaged() },
    )
}

/** The kind to show for [change] inside the staged or unstaged list. */
fun GitStatus.Change.kindIn(staged: Boolean): GitStatus.Kind = if (staged) this.staged() else this.unstaged()

fun kindLetter(kind: GitStatus.Kind): String = when (kind) {
    GitStatus.Kind.ADDED -> "A"
    GitStatus.Kind.MODIFIED -> "M"
    GitStatus.Kind.DELETED -> "D"
    GitStatus.Kind.RENAMED -> "R"
    GitStatus.Kind.COPIED -> "C"
    GitStatus.Kind.TYPE_CHANGED -> "T"
    GitStatus.Kind.UNTRACKED -> "U"
    GitStatus.Kind.UNMERGED -> "!"
    GitStatus.Kind.NONE -> ""
}

fun kindLabel(kind: GitStatus.Kind): String = when (kind) {
    GitStatus.Kind.ADDED -> "Added"
    GitStatus.Kind.MODIFIED -> "Modified"
    GitStatus.Kind.DELETED -> "Deleted"
    GitStatus.Kind.RENAMED -> "Renamed"
    GitStatus.Kind.COPIED -> "Copied"
    GitStatus.Kind.TYPE_CHANGED -> "Type changed"
    GitStatus.Kind.UNTRACKED -> "New file"
    GitStatus.Kind.UNMERGED -> "Conflict"
    GitStatus.Kind.NONE -> ""
}
