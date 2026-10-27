package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.Worktree
import java.nio.file.InvalidPathException
import java.nio.file.Path

fun worktreeTitle(worktree: Worktree): String =
    worktree.branch().takeIf { it.isNotBlank() } ?: "detached ${worktree.head().orEmpty().take(SHORT_HASH)}"

/** Uncommitted changes die with the folder, so removing a dirty worktree needs an explicit "discard". */
fun removalNeedsForce(worktree: Worktree): Boolean = worktree.dirty()

/** Keeps a path the user typed; follows the suggestion for as long as the field still holds the previous suggestion. */
fun refilledPath(current: String, previousSuggestion: String, nextSuggestion: String): String =
    if (current.isBlank() || current == previousSuggestion) nextSuggestion else current

private const val DIRECTORY_INFIX = "-wt-"

/** A sibling of the repository folder named `<repo>-wt-<branch>`; empty when the branch cannot form a valid path. */
fun defaultWorktreePath(top: Path, branch: String): String =
    try {
        top.fileName?.let { top.resolveSibling(it.toString() + DIRECTORY_INFIX + branch.replace('/', '-')).toString() }.orEmpty()
    } catch (e: InvalidPathException) {
        ""
    }
