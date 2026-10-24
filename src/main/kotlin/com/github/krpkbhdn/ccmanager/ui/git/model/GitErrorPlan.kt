package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitException

/** Next steps the UI can offer for a failed git command. */
enum class ErrorAction(val label: String) {
    PULL("Pull"),
    FORCE_PUSH("Force push with lease"),
    SHOW_CONFLICTS("Show conflicts"),
    RETRY("Retry"),
    COPY_FETCH_COMMAND("Copy git fetch"),
    SET_UPSTREAM("Push and set upstream"),
    STASH_AND_RETRY("Stash and retry"),
    SHOW_DETAILS("Details"),
}

/** A [GitException] turned into what to tell the user: [title], one-line [headline], optional [hint] and the [actions] on offer. */
data class ErrorPlan(
    val kind: GitException.Kind,
    val title: String,
    val headline: String,
    val hint: String?,
    val details: String,
    val actions: List<ErrorAction>,
)

private const val AUTH_HINT = "Git cannot ask for a password here. Sign in once in a terminal (git fetch), or set up a credential helper or SSH key, then retry."
private const val MISSING_HINT = "Git was not found on this machine. Install it from https://git-scm.com/downloads and restart the app."
private const val LOCKED_HINT = "Another git process is using the repository, or a stale .git/index.lock is left over."

fun errorPlan(error: GitException, label: String): ErrorPlan {
    val details = error.output().orEmpty().ifBlank { error.message.orEmpty() }
    val headline = error.message.orEmpty().ifBlank { "git failed" }
    val (hint, actions) = when (error.kind()) {
        GitException.Kind.AUTH -> AUTH_HINT to listOf(ErrorAction.COPY_FETCH_COMMAND, ErrorAction.RETRY)
        GitException.Kind.NON_FAST_FORWARD -> "The remote has commits you do not have." to listOf(ErrorAction.PULL, ErrorAction.FORCE_PUSH)
        GitException.Kind.CONFLICT -> "Resolve the conflicted files, then continue or abort." to listOf(ErrorAction.SHOW_CONFLICTS)
        GitException.Kind.LOCKED -> LOCKED_HINT to listOf(ErrorAction.RETRY)
        GitException.Kind.GIT_MISSING -> MISSING_HINT to emptyList()
        GitException.Kind.NO_UPSTREAM -> "This branch does not track a remote branch yet." to listOf(ErrorAction.SET_UPSTREAM)
        GitException.Kind.DIRTY_TREE -> "Local changes would be overwritten." to listOf(ErrorAction.STASH_AND_RETRY)
        GitException.Kind.TIMEOUT -> "The command took too long." to listOf(ErrorAction.RETRY)
        else -> null to emptyList()
    }
    val withDetails = if (details.lines().size > 1 || details.length > headline.length) actions + ErrorAction.SHOW_DETAILS else actions
    return ErrorPlan(error.kind(), label, headline, hint, details, withDetails)
}
