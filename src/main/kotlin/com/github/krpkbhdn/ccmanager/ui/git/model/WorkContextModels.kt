package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.TaskWorktree
import com.github.krpkbhdn.ccmanager.ui.state.WorkContext
import java.nio.file.Path

private const val MAIN_FOLDER = "main folder"
private const val DETACHED = "detached"

/** What a context shows in the selector: the main folder with its branch, a worktree by its branch (its folder name when detached). */
fun contextTitle(context: WorkContext): String {
    val name = context.branch ?: if (context.main) DETACHED else context.directory.fileName?.toString() ?: DETACHED
    return if (context.main) "$MAIN_FOLDER · $name" else name
}

/** The selector is only worth showing when the project has a linked worktree besides its main folder. */
fun contextSelectorVisible(contexts: List<WorkContext>): Boolean = contexts.size > 1

/** Selector button text: the active context, the main folder when none is marked. */
fun contextSelectorLabel(contexts: List<WorkContext>): String =
    (contexts.firstOrNull { it.active } ?: contexts.firstOrNull { it.main })?.let(::contextTitle) ?: MAIN_FOLDER

/** The folder a pick switches to; null for the main folder, which is shown when no worktree is active. */
fun WorkContext.switchTarget(): Path? = directory.takeUnless { main }

/** What the banner under the toolbar says about the active linked worktree; [task] is false for a worktree without a task row. */
data class WorkBanner(val branch: String, val base: String?, val taskLine: String?, val task: Boolean)

/** Null when the active folder is the main one and has no task (nothing to announce). */
fun workBanner(branch: String?, task: TaskWorktree?, linkedWorktree: Boolean): WorkBanner? {
    if (task == null && !linkedWorktree) return null
    val line = task?.task()?.lineSequence()?.map(String::trim)?.firstOrNull { it.isNotEmpty() }
    return WorkBanner(task?.branch()?.takeIf { it.isNotBlank() } ?: branch ?: DETACHED, task?.baseBranch()?.takeIf { it.isNotBlank() }, line, task != null)
}
