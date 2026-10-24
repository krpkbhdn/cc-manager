package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.DiffState
import com.github.krpkbhdn.ccmanager.ui.state.FileTreeEntry
import com.github.krpkbhdn.ccmanager.ui.state.fileName
import com.github.krpkbhdn.ccmanager.ui.state.fileTree
import com.github.krpkbhdn.ccmanager.ui.state.selectedFile
import com.github.krpkbhdn.ccmanager.ui.state.totalAdditions
import com.github.krpkbhdn.ccmanager.ui.state.totalDeletions
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

private val fileListWidth = 296.dp
private const val SKELETON_FILES = 6

/** Full-height review: changed-file tree on the left, the selected file's unified diff on the right. */
@Composable
fun DiffTab(state: DiffState, onRefresh: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        DiffToolbar(state, onRefresh)
        HorizontalRule()
        DiffBody(state)
    }
}

@Composable
private fun DiffToolbar(state: DiffState, onRefresh: () -> Unit) {
    val diff = state.diff
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label("Changes", style = Type.heading)
        if (diff != null && diff.gitRepository && diff.files.isNotEmpty()) {
            Label("${diff.files.size} ${if (diff.files.size == 1) "file" else "files"}", color = colors.textSecondary, style = Type.small)
            Label("+${totalAdditions(diff)}", color = colors.success, style = Type.numeric)
            Label("-${totalDeletions(diff)}", color = colors.danger, style = Type.numeric)
        }
        Box(Modifier.weight(1f))
        if (state.loading) Spinner(size = 14.dp)
        IconButton(Icons.Refresh, onRefresh, tooltip = "Refresh changes", enabled = !state.loading)
    }
}

@Composable
private fun DiffBody(state: DiffState) {
    val diff = state.diff
    when {
        state.error != null -> EmptyState(Icons.Alert, "Could not read the changes", state.error, tint = colors.danger)
        diff == null -> DiffSkeleton()
        !diff.gitRepository -> EmptyState(Icons.Branch, "Not a git repository", "Run git init in this folder to see what the agents changed.")
        diff.files.isEmpty() -> EmptyState(Icons.CircleCheck, "Working tree is clean", "Nothing differs from HEAD yet. Changes show up here as agents edit files.", tint = colors.success)
        else -> DiffContent(diff.files)
    }
}

@Composable
private fun DiffSkeleton() {
    Row(Modifier.fillMaxSize()) {
        Column(Modifier.width(fileListWidth).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(SKELETON_FILES) { SkeletonBlock(Modifier.fillMaxWidth().padding(end = (it % 3 * 24).dp).height(26.dp), RoundedCornerShape(Radius.sm)) }
        }
        VerticalRule()
        Column(Modifier.weight(1f).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(SKELETON_FILES + 2) { SkeletonBlock(Modifier.fillMaxWidth(0.4f + (it % 4) * 0.15f).height(16.dp), RoundedCornerShape(Radius.xs)) }
        }
    }
}

@Composable
private fun DiffContent(files: List<GitDiff.FileDiff>) {
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val selected = selectedFile(files, selectedPath)
    val tree = remember(files) { fileTree(files) }
    val listState = rememberLazyListState()
    Row(Modifier.fillMaxSize()) {
        Box(Modifier.width(fileListWidth).fillMaxHeight()) {
            LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(Spacing.sm)) {
                items(tree.size) { index ->
                    when (val entry = tree[index]) {
                        is FileTreeEntry.Directory -> DirectoryHeading(entry.path)
                        is FileTreeEntry.File -> FileRow(entry.file, entry.file == selected) { selectedPath = entry.file.path }
                    }
                }
            }
            ThinScrollbar(listState)
        }
        VerticalRule()
        selected?.let { FileDiffView(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun DirectoryHeading(path: String) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.sm, end = Spacing.sm, top = Spacing.md, bottom = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
        Glyph(Icons.FolderOpen, colors.textTertiary, size = IconSize.small)
        Label(path, color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
    }
}

@Composable
internal fun changeTint(change: GitDiff.ChangeType): Color = when (change) {
    GitDiff.ChangeType.ADDED, GitDiff.ChangeType.UNTRACKED -> colors.success
    GitDiff.ChangeType.MODIFIED -> colors.warning
    GitDiff.ChangeType.DELETED -> colors.danger
    GitDiff.ChangeType.RENAMED -> colors.accent
}

internal fun changeIcon(change: GitDiff.ChangeType): ImageVector = when (change) {
    GitDiff.ChangeType.ADDED, GitDiff.ChangeType.UNTRACKED -> Icons.FilePlus
    GitDiff.ChangeType.DELETED -> Icons.FileMinus
    else -> Icons.FileEdit
}

@Composable
private fun FileRow(file: GitDiff.FileDiff, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.sm), base = if (selected) colors.accentSoft else Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(changeIcon(file.change), changeTint(file.change), size = IconSize.medium)
            Label(fileName(file.path), Modifier.weight(1f), color = if (selected) colors.textPrimary else colors.textSecondary, style = Type.smallMedium, tooltipWhenTruncated = true)
            if (!file.binary) {
                if (file.additions > 0) Label("+${file.additions}", color = colors.success, style = Type.numericCaption)
                if (file.deletions > 0) Label("-${file.deletions}", color = colors.danger, style = Type.numericCaption)
            }
        }
    }
}
