package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide
import com.github.krpkbhdn.ccmanager.git.GitPatches
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.git.GitSystemActions
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeGroup
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeItem
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeRef
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeSections
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSubView
import com.github.krpkbhdn.ccmanager.ui.git.model.applyPatch
import com.github.krpkbhdn.ccmanager.ui.git.model.changeItems
import com.github.krpkbhdn.ccmanager.ui.git.model.changeSections
import com.github.krpkbhdn.ccmanager.ui.git.model.markResolved
import com.github.krpkbhdn.ccmanager.ui.git.model.requestDiscard
import com.github.krpkbhdn.ccmanager.ui.git.model.resolveConflict
import com.github.krpkbhdn.ccmanager.ui.git.model.stage
import com.github.krpkbhdn.ccmanager.ui.git.model.unstage
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import com.github.krpkbhdn.ccmanager.workspace.UnifiedDiffParser

private val minListWidth = 300.dp
private val maxListWidth = 400.dp
private const val LIST_SHARE = 0.42f

/** The heart of the Git tab: file lists with the commit box on the left, the selected file's diff on the right. */
@Composable
fun ChangesView(git: GitState, system: GitSystemActions, modifier: Modifier = Modifier) {
    val status = git.status ?: return
    val sections = remember(status) { changeSections(status.changes()) }
    if (sections.isClean) {
        EmptyState(Icons.CircleCheck, "Working tree is clean", "Nothing to commit. Changes show up here as you or the agents edit files.", modifier, tint = colors.success)
        return
    }
    LaunchedEffect(sections) { if (git.selected == null) git.selected = firstChange(sections, git.flat) }
    BoxWithConstraints(modifier.fillMaxSize()) {
        val listWidth = (maxWidth * LIST_SHARE).coerceIn(minListWidth, maxListWidth)
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.width(listWidth).fillMaxHeight()) {
                ListHeader(git, sections.dirtyCount)
                HorizontalRule()
                ChangeListView(sections, git.selected, git.flat, listActions(git, system), Modifier.weight(1f).fillMaxWidth())
                HorizontalRule()
                CommitBox(git, sections.staged.size, sections.conflicts.size)
            }
            VerticalRule()
            DiffArea(git, sections.conflicts.any { it.path() == git.selected?.path }, system, Modifier.weight(1f))
        }
    }
}

/** What to show first, in list order: a conflict, else an unstaged file, else a staged one. */
internal fun firstChange(sections: ChangeSections, flat: Boolean): ChangeRef? {
    val files = changeItems(sections, flat, emptySet(), emptySet()).filterIsInstance<ChangeItem.File>()
    val first = files.firstOrNull { it.group == ChangeGroup.CONFLICTS } ?: files.firstOrNull { it.group == ChangeGroup.UNSTAGED } ?: files.firstOrNull()
    return first?.let { ChangeRef(it.row.change.path(), it.group == ChangeGroup.STAGED) }
}

@Composable
private fun ListHeader(git: GitState, fileCount: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("$fileCount ${if (fileCount == 1) "file" else "files"} changed", Modifier.weight(1f), color = colors.textSecondary, style = Type.small)
        IconButton(if (git.flat) Icons.FolderTree else Icons.List, { git.setFlatChanges(!git.flat) }, tooltip = if (git.flat) "Group by folder" else "Show as flat list", size = 26.dp)
    }
}

private fun listActions(git: GitState, system: GitSystemActions) = ChangeListActions(
    onSelect = { git.selected = it },
    onStage = git::stage,
    onUnstage = git::unstage,
    onDiscard = git::requestDiscard,
    onShowHistory = { path -> git.log.update { it.forPath(path) }; git.show(GitSubView.LOG) },
    onOpenFolder = system.openFolder,
    onOpenIde = system.openInIde,
    onResolve = git::resolveConflict,
    onMarkResolved = git::markResolved,
)

@Composable
private fun DiffArea(git: GitState, conflicted: Boolean, system: GitSystemActions, modifier: Modifier) {
    val ref = git.selected
    val file by produceState<GitDiff.FileDiff?>(null, ref, git.status) {
        if (value?.path != ref?.path) value = null
        value = ref?.let { git.diffOf(it) }
    }
    Column(modifier.fillMaxHeight()) {
        if (conflicted && ref != null) ConflictBar(git, ref, system)
        val context = if (ref?.staged == true) DiffContext.STAGED else DiffContext.UNSTAGED
        val current = file
        GitDiffPane(current, context, Modifier.weight(1f), current?.let { hunkActions(git, it, context) }, loading = ref != null, busy = git.busy)
    }
}

/** Null for a cut-off diff: a patch built from it would be corrupt. */
private fun hunkActions(git: GitState, file: GitDiff.FileDiff, context: DiffContext): HunkActions? {
    if (file.lines.any { it.text == UnifiedDiffParser.TRUNCATED_MARKER }) return null
    val reverse = context == DiffContext.STAGED
    return HunkActions(
        onApplyHunk = { hunk -> git.applyPatch(reverse) { GitPatches.hunk(file, hunk.number) } },
        onApplyLines = { hunk, lines ->
            git.applyPatch(reverse) { if (reverse) GitPatches.linesForUnstaging(file, hunk.number, lines) else GitPatches.lines(file, hunk.number, lines) }
        },
    )
}

@Composable
private fun ConflictBar(git: GitState, ref: ChangeRef, system: GitSystemActions) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Label("Conflict in ${ref.path}", Modifier.weight(1f), color = colors.danger, style = Type.smallMedium, tooltipWhenTruncated = true)
        AppButton("Accept ours", { git.resolveConflict(ref.path, ConflictSide.OURS) }, compact = true, enabled = !git.busy)
        AppButton("Accept theirs", { git.resolveConflict(ref.path, ConflictSide.THEIRS) }, compact = true, enabled = !git.busy)
        AppButton("Mark resolved", { git.markResolved(ref.path) }, compact = true, enabled = !git.busy)
        AppButton("Open in IDE", { system.openInIde(ref.path) }, compact = true, icon = Icons.Code, enabled = system.ideAvailable)
    }
    HorizontalRule()
}
