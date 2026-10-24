package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.SplitActionButton
import com.github.krpkbhdn.ccmanager.ui.git.branch.BranchPopup
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.fetch
import com.github.krpkbhdn.ccmanager.ui.git.model.pull
import com.github.krpkbhdn.ccmanager.ui.git.model.push
import com.github.krpkbhdn.ccmanager.ui.git.model.requestForcePush
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val compactWidth = 640.dp
private val chipHeight = 28.dp
private val chipMaxWidth = 240.dp

/** Branch chip with ahead / behind counters on the left; Fetch, Pull, Push, Stash and refresh on the right. */
@Composable
fun GitToolbar(git: GitState, modifier: Modifier = Modifier) {
    val status = git.status
    BoxWithConstraints(modifier.fillMaxWidth()) {
        val compact = maxWidth < compactWidth
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            BranchChip(git)
            if (status != null) Counters(status.ahead(), status.behind(), status.upstream())
            Box(Modifier.weight(1f))
            val enabled = !git.busy && status?.repository() == true
            val busyLabel = git.job?.label
            if (compact) {
                IconButton(Icons.Download, { git.fetch() }, tooltip = "Fetch all remotes", enabled = enabled)
            } else {
                AppButton("Fetch", { git.fetch() }, icon = Icons.Download, enabled = enabled, loading = busyLabel == "Fetching", compact = true, tooltip = "Fetch all remotes")
            }
            PullButton(git, compact, enabled)
            PushButton(git, compact, enabled)
            IconButton(Icons.Archive, { git.dialog = GitDialog.SaveStash() }, tooltip = "Stash changes", enabled = enabled)
            IconButton(Icons.Refresh, git::refresh, tooltip = "Refresh", enabled = !git.loading)
            if (git.loading) Spinner(size = 14.dp)
        }
    }
}

@Composable
private fun BranchChip(git: GitState) {
    val status = git.status
    val shape = RoundedCornerShape(Radius.md)
    val label = status?.branch() ?: status?.head()?.take(DETACHED_HASH)?.let { "detached $it" } ?: "Loading..."
    Box {
        Pressable({ git.branchPopupOpen = true }, Modifier.height(chipHeight).border(1.dp, colors.borderStrong, shape), shape, base = colors.surfaceRaised, hover = colors.surfaceHover, enabled = status?.repository() == true) {
            Row(Modifier.padding(horizontal = Spacing.md - 2.dp).height(chipHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm - 1.dp)) {
                Glyph(Icons.Branch, colors.accent, size = IconSize.large)
                Label(label, Modifier.widthIn(max = chipMaxWidth), color = colors.textPrimary, style = Type.monoMedium, tooltipWhenTruncated = true)
                Glyph(Icons.ChevronsUpDown, colors.textTertiary, size = IconSize.medium)
            }
        }
        if (git.branchPopupOpen) BranchPopup(git, onDismiss = { git.branchPopupOpen = false })
    }
}

private const val DETACHED_HASH = 7

@Composable
private fun Counters(ahead: Int, behind: Int, upstream: String?) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        if (ahead > 0) Counter(Icons.ArrowUp, ahead, colors.success)
        if (behind > 0) Counter(Icons.ArrowDown, behind, colors.warning)
        if (upstream == null) Label("not published", color = colors.textTertiary, style = Type.caption)
    }
}

@Composable
private fun Counter(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, tint: androidx.compose.ui.graphics.Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        Glyph(icon, tint, size = IconSize.medium)
        Label(count.toString(), color = tint, style = Type.numeric)
    }
}

@Composable
private fun PullButton(git: GitState, compact: Boolean, enabled: Boolean) {
    val modes = listOf(PullMode.MERGE to "Pull (merge)", PullMode.REBASE to "Pull (rebase)", PullMode.FAST_FORWARD_ONLY to "Pull (fast-forward only)")
    val menu = modes.map { (mode, text) -> MenuEntry(text, if (mode == git.pullMode) Icons.Check else null) { git.setPullMode(mode); git.pull(mode) } }
    SplitActionButton(if (compact) null else "Pull", Icons.Pull, { git.pull() }, menu, loading = git.job?.label == "Pulling", enabled = enabled, tooltip = "Pull: ${modes.first { it.first == git.pullMode }.second.substringAfter('(').trimEnd(')')}")
}

@Composable
private fun PushButton(git: GitState, compact: Boolean, enabled: Boolean) {
    val publish = git.status?.upstream() == null
    val menu = listOf(
        MenuEntry(if (publish) "Publish branch" else "Push", Icons.Push, hint = AppShortcut.GIT_PUSH.hint()) { git.push() },
        MenuEntry("Force push with lease...", Icons.Alert, danger = true) { git.requestForcePush() },
        MenuEntry("Push tags", Icons.Tag) { git.push(tags = true) },
    )
    SplitActionButton(if (compact) null else if (publish) "Publish" else "Push", Icons.Push, { git.push() }, menu, loading = git.job?.label == "Pushing", enabled = enabled, tooltip = if (publish) "Publish branch and set upstream" else "Push to upstream")
}
