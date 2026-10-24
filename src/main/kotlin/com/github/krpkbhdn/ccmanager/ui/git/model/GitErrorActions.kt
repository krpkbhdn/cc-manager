package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard

private const val FETCH_COMMAND = "git fetch"
private const val STASH_MESSAGE = "WIP stashed by Claude Code Manager"

/** Runs one of the follow-ups offered on [GitState.error]; the strip closes unless the action keeps it relevant. */
fun GitState.perform(action: ErrorAction) {
    val plan = error
    when (action) {
        ErrorAction.PULL -> { error = null; pull() }
        ErrorAction.FORCE_PUSH -> { error = null; requestForcePush() }
        ErrorAction.SHOW_CONFLICTS -> { error = null; show(GitSubView.CHANGES) }
        ErrorAction.RETRY -> { error = null; retryLast() }
        ErrorAction.COPY_FETCH_COMMAND -> copyToClipboard(FETCH_COMMAND)
        ErrorAction.SET_UPSTREAM -> { error = null; push() }
        ErrorAction.STASH_AND_RETRY -> { error = null; stashThenRetry() }
        ErrorAction.SHOW_DETAILS -> plan?.let { dialog = GitDialog.Output(it.title, it.details) }
    }
}

private fun GitState.stashThenRetry() = act("Stashing", onSuccess = { _: Unit -> retryLast() }) { stashes.push(repo, STASH_MESSAGE, false) }
