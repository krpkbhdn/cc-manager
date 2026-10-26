package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitMisc.Stash
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.GhostButton
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.git.changes.DiffContext
import com.github.krpkbhdn.ccmanager.ui.git.changes.GitDiffPane
import com.github.krpkbhdn.ccmanager.ui.git.log.DiffFileList
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.commitAge
import com.github.krpkbhdn.ccmanager.ui.git.model.requestDropStash
import com.github.krpkbhdn.ccmanager.ui.git.model.stashApply
import com.github.krpkbhdn.ccmanager.ui.git.model.stashDiff
import com.github.krpkbhdn.ccmanager.ui.git.model.stashPop
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.time.Instant

private val listWidth = 440.dp

/** Stashes with Apply / Pop / Drop; selecting one shows its files and diff on the right. */
@Composable
fun StashesView(git: GitState, now: () -> Instant, modifier: Modifier = Modifier) {
    var pickedHash by remember { mutableStateOf<String?>(null) }
    val selected = git.stashes.firstOrNull { it.hash() == pickedHash } ?: git.stashes.firstOrNull()
    var chosenFile by remember { mutableStateOf<String?>(null) }
    val diff by produceState<GitDiff?>(null, selected, git.stashes) { value = selected?.let { git.stashDiff(it.hash()) } }
    Column(modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Label("${git.stashes.size} ${if (git.stashes.size == 1) "stash" else "stashes"}", Modifier.weight(1f), color = colors.textSecondary, style = Type.small)
            AppButton("Stash changes", { git.dialog = GitDialog.SaveStash() }, variant = ButtonVariant.PRIMARY, icon = Icons.Archive, compact = true)
        }
        HorizontalRule()
        if (git.stashes.isEmpty()) {
            EmptyState(Icons.Archive, "No stashes", "Stash parks your uncommitted changes so you can switch context safely.")
            return@Column
        }
        Row(Modifier.fillMaxSize()) {
            LazyColumn(Modifier.width(listWidth).fillMaxHeight(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm)) {
                items(git.stashes, key = { it.hash() }) { stash -> StashRow(git, stash, now, stash.hash() == selected?.hash()) { pickedHash = stash.hash(); chosenFile = null } }
            }
            VerticalRule()
            val files = diff?.files().orEmpty()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                if (files.isEmpty()) {
                    EmptyState(Icons.Diff, "Select a stash", "Its files and diff show up here.")
                } else {
                    val shown = chosenFile?.takeIf { p -> files.any { it.path == p } } ?: files.first().path
                    DiffFileList(files, shown) { chosenFile = it }
                    HorizontalRule()
                    GitDiffPane(files.firstOrNull { it.path == shown }, DiffContext.READ_ONLY, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StashRow(git: GitState, stash: Stash, now: () -> Instant, selected: Boolean, onSelect: () -> Unit) {
    Pressable(onSelect, Modifier.fillMaxWidth().padding(vertical = 1.dp), RoundedCornerShape(Radius.sm), base = if (selected) colors.accentSoft else Color.Transparent) {
        Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("stash@{${stash.index()}}", color = colors.accent, style = Type.monoSmall)
                Label(stash.message(), Modifier.weight(1f), color = colors.textPrimary, style = Type.smallMedium, tooltipWhenTruncated = true)
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label("on ${stash.branch()}  ·  ${commitAge(stash.date(), now())}", Modifier.weight(1f), color = colors.textTertiary, style = Type.caption)
                GhostButton("Apply", { git.stashApply(stash) }, enabled = !git.busy)
                GhostButton("Pop", { git.stashPop(stash) }, enabled = !git.busy)
                GhostButton("Drop", { git.requestDropStash(stash) }, enabled = !git.busy)
            }
        }
    }
}
