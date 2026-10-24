package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode
import com.github.krpkbhdn.ccmanager.git.GitMisc.PushOptions
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest

fun GitState.fetch() = act("Fetching") { sync.fetch(repo, it) }

fun GitState.pull(mode: PullMode = pullMode) = act("Pulling", onSuccess = { _: Unit -> success("Pull complete") }) { sync.pull(repo, mode, it) }

/** Pushes the current branch; without an upstream it publishes the branch and sets one. */
fun GitState.push(forceWithLease: Boolean = false, tags: Boolean = false) {
    val options = PushOptions(forceWithLease, status?.upstream() == null && !tags, tags)
    val what = if (tags) "tags" else status?.branch() ?: "HEAD"
    act("Pushing", onSuccess = { _: Unit -> success("Pushed $what") }) { sync.push(repo, options, it) }
}

fun GitState.requestForcePush() {
    dialog = GitDialog.Confirm(
        ConfirmRequest(
            "Force push with lease",
            "Overwrite the remote branch with yours? It is refused if someone pushed in the meantime.",
            "Force push",
        ) { push(forceWithLease = true) },
    )
}
