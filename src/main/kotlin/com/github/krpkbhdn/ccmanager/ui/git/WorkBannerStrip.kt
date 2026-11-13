package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.WorkBanner
import com.github.krpkbhdn.ccmanager.ui.git.model.canCreatePullRequest
import com.github.krpkbhdn.ccmanager.ui.git.model.mergedCleanupAvailable
import com.github.krpkbhdn.ccmanager.ui.git.model.requestMergedCleanup
import com.github.krpkbhdn.ccmanager.ui.git.model.startPullRequest
import com.github.krpkbhdn.ccmanager.ui.git.model.startTaskMerge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.WorkContext
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val stackedWidth = 640.dp

/** Under the toolbar in a linked worktree: its branch, base and task, the session working there, and what to do next (merge, pull request, leave). Actions drop below the text when it is narrow. */
@Composable
fun WorkBannerStrip(banner: WorkBanner, git: GitState, work: WorkContextActions) {
    BoxWithConstraints(Modifier.fillMaxWidth().background(colors.accent.copy(alpha = Alpha.FAINT))) {
        val stacked = maxWidth < stackedWidth
        val info = @Composable { rowModifier: Modifier -> BannerDetails(banner, work, rowModifier) }
        val actions = @Composable { BannerActions(banner, git, work) }
        val padding = Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm)
        if (stacked) {
            Column(padding, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                info(Modifier)
                actions()
            }
        } else {
            Row(padding, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                info(Modifier.weight(1f))
                actions()
            }
        }
    }
}

@Composable
private fun BannerDetails(banner: WorkBanner, work: WorkContextActions, modifier: Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Glyph(Icons.FolderTree, colors.accent)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Label(banner.branch, Modifier.weight(1f, fill = false), color = colors.textPrimary, style = Type.monoMedium, tooltipWhenTruncated = true)
                Label(if (banner.base != null) "based on ${banner.base}" else if (banner.task) "" else "linked worktree", color = colors.textTertiary, style = Type.caption)
            }
            banner.taskLine?.let { Label(it, color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true) }
            work.active?.let { SessionLine(it, work) }
        }
    }
}

@Composable
private fun SessionLine(context: WorkContext, work: WorkContextActions) {
    val session = context.sessionId?.let(work.sessionOf)
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        if (session == null) {
            Label("No session here", color = colors.textTertiary, style = Type.caption)
        } else {
            StatusDot(session.status, size = 7.dp)
            Label(session.title, Modifier.weight(1f, fill = false), color = colors.textSecondary, style = Type.caption, tooltipWhenTruncated = true)
            Pressable({ work.openSession(session.id) }) { Label("Open session", color = colors.accent, style = Type.captionMedium) }
        }
    }
}

@Composable
private fun BannerActions(banner: WorkBanner, git: GitState, work: WorkContextActions) {
    val idle = !git.busy && git.dialog == null
    val context = work.active
    FlowRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xs), itemVerticalAlignment = Alignment.CenterVertically) {
        if (context != null && context.sessionId == null) {
            AppButton("Open session here", { work.openSessionHere(context.directory) }, icon = Icons.Play, compact = true, tooltip = "Start a session in this worktree")
        }
        if (git.mergedCleanupAvailable()) {
            AppButton("Merged: remove worktree and branch", { git.requestMergedCleanup() }, icon = Icons.Trash, enabled = idle, compact = true, tooltip = "Remove this worktree, its branch and its run configurations")
        } else if (banner.task && git.mergedInto == null) {
            AppButton("Merge into ${banner.base ?: "base"}", { git.startTaskMerge() }, variant = ButtonVariant.PRIMARY, icon = Icons.Merge, enabled = idle && git.noChangesHint == null, compact = true, tooltip = git.noChangesHint ?: "Merge this branch into its base here, without pushing")
        }
        if (canCreatePullRequest(git.status?.branch(), git.ghStatus, git.pullRequest, git.forgeRemote)) {
            val forge = git.forgeRemote!!.forge
            AppButton("Create ${forge.shortName}...", { git.startPullRequest() }, icon = Icons.Push, enabled = idle && git.noChangesHint == null, compact = true, tooltip = git.noChangesHint ?: "Draft a ${forge.longName} for this branch")
        }
        AppButton("Back to main folder", { work.switchTo(null) }, icon = Icons.Undo, compact = true, tooltip = "Show the project's main folder; this worktree stays")
    }
}
