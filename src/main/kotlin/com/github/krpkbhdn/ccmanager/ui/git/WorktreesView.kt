package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.Worktree
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.sameFolder
import com.github.krpkbhdn.ccmanager.ui.git.model.requestRemoveWorktree
import com.github.krpkbhdn.ccmanager.ui.git.model.worktreeTitle
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val rowHeight = 44.dp
private val titleWidth = 200.dp

/** Every worktree of the repository with its state; each can be opened as a project here or removed. */
@Composable
fun WorktreesView(git: GitState, work: WorkContextActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Label("${git.worktrees.size} ${if (git.worktrees.size == 1) "worktree" else "worktrees"}", Modifier.weight(1f), color = colors.textSecondary, style = Type.small)
            AppButton("Create", { git.dialog = GitDialog.CreateWorktree }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, compact = true)
        }
        HorizontalRule()
        if (git.worktrees.isEmpty()) {
            EmptyState(Icons.FolderTree, "No worktrees", "A worktree gives a branch its own folder, so agents can work on several branches at once.")
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(Spacing.sm)) {
            items(git.worktrees, key = { it.path().toString() }) { WorktreeRow(git, work, it) }
        }
    }
}

@Composable
private fun WorktreeRow(git: GitState, work: WorkContextActions, worktree: Worktree) {
    val context = work.contexts.firstOrNull { sameFolder(it.directory, worktree.path()) }
    Row(Modifier.fillMaxWidth().height(rowHeight).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Glyph(Icons.FolderTree, colors.accent, size = IconSize.large)
        Label(worktreeTitle(worktree), Modifier.widthIn(max = titleWidth).weight(1f, fill = false), color = colors.textPrimary, style = Type.monoMedium, tooltipWhenTruncated = true)
        Label(worktree.path().toString(), Modifier.weight(1f), color = colors.textSecondary, style = Type.monoSmall, tooltipWhenTruncated = true)
        if (worktree.main()) Chip("main", colors.textSecondary)
        context?.task?.let { WithTooltip(it.task()) { Chip("task", colors.accent) } }
        if (worktree.dirty()) Chip("dirty", colors.warning)
        if (worktree.merged()) Chip("merged", colors.success)
        if (worktree.locked()) Chip("locked", colors.textSecondary)
        IconButton(Icons.Eye, { work.switchTo(worktree.path()) }, tooltip = "Show in Git tab", size = 24.dp, tint = colors.textTertiary)
        IconButton(Icons.Play, { work.openSessionHere(worktree.path()) }, tooltip = "Open session here", size = 24.dp, tint = colors.textTertiary)
        IconButton(Icons.SquareTerminal, { work.openTerminalHere(worktree.path()) }, tooltip = "Terminal here", size = 24.dp, tint = colors.textTertiary)
        IconButton(Icons.FolderOpen, { git.host.openDirectory(worktree.path()) }, tooltip = "Open folder", size = 24.dp, tint = colors.textTertiary)
        if (!worktree.main()) IconButton(Icons.Trash, { git.requestRemoveWorktree(worktree) }, tooltip = "Remove worktree", size = 24.dp, tint = colors.textTertiary)
    }
}
