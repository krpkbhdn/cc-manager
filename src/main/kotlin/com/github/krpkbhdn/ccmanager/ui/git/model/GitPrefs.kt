package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode

/** Remembered Git tab choices: last section per project, the pull strategy, flat or tree file lists. */
data class GitPrefs(
    val views: Map<Long, GitSubView> = emptyMap(),
    val pullMode: PullMode = PullMode.MERGE,
    val flatChanges: Boolean = false,
) {
    fun viewOf(projectId: Long): GitSubView = views[projectId] ?: GitSubView.CHANGES

    fun withView(projectId: Long, view: GitSubView): GitPrefs = copy(views = views + (projectId to view))
}
