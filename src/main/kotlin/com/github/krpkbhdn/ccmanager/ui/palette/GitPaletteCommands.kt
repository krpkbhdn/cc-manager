package com.github.krpkbhdn.ccmanager.ui.palette

import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.fetch
import com.github.krpkbhdn.ccmanager.ui.git.model.openWorktree
import com.github.krpkbhdn.ccmanager.ui.git.model.worktreeTitle
import com.github.krpkbhdn.ccmanager.ui.git.model.pull
import com.github.krpkbhdn.ccmanager.ui.git.model.push
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route

/** "Git: ..." entries for the project on screen; each one opens the Git tab where its result is visible. */
fun gitActions(state: AppState): List<PaletteItem> {
    val projectId = (state.route as? Route.Project)?.id ?: return emptyList()
    val git = state.gitOf(projectId) ?: return emptyList()
    val here = state.projects.nameOf(projectId)
    fun item(id: String, title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, shortcut: AppShortcut? = null, run: (GitState) -> Unit) =
        PaletteItem("git-$id", "Git: $title", PaletteGroup.ACTIONS, "in $here", icon, shortcut = shortcut) { run(state.openGit(projectId) ?: git) }
    return listOf(
        item("tab", "Open Git tab", Icons.Branch, AppShortcut.GIT_TAB) { },
        item("log", "Show log", Icons.Log) { it.show(GitSubView.LOG) },
        item("branches", "Show branches", Icons.Layers) { it.show(GitSubView.BRANCHES) },
        item("checkout", "Checkout branch...", Icons.Branch, AppShortcut.GIT_BRANCHES) { it.branchPopupOpen = true },
        item("new-branch", "New branch...", Icons.Plus) { it.dialog = GitDialog.NewBranch(null) },
        item("fetch", "Fetch", Icons.Download) { it.fetch() },
        item("pull", "Pull", Icons.Pull) { it.pull() },
        item("push", "Push", Icons.Push, AppShortcut.GIT_PUSH) { it.push() },
        item("commit", "Commit...", Icons.Check, AppShortcut.GIT_COMMIT) { it.requestCommitFocus() },
        item("stash", "Stash changes...", Icons.Archive) { it.dialog = GitDialog.SaveStash() },
        item("create-worktree", "Create worktree...", Icons.FolderTree) { it.show(GitSubView.WORKTREES); it.dialog = GitDialog.CreateWorktree },
    ) + git.worktrees.map { worktree ->
        item("worktree-${worktree.path()}", "Switch worktree: ${worktreeTitle(worktree)}", Icons.FolderTree) { it.openWorktree(worktree, false) }
    }
}
