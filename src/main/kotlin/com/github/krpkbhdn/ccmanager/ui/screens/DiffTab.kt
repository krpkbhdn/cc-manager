package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.state.DiffState
import com.github.krpkbhdn.ccmanager.ui.state.changeLetter
import com.github.krpkbhdn.ccmanager.ui.state.fileName
import com.github.krpkbhdn.ccmanager.ui.state.lineMarker
import com.github.krpkbhdn.ccmanager.ui.state.lineNumberText
import com.github.krpkbhdn.ccmanager.ui.state.parentDirectory
import com.github.krpkbhdn.ccmanager.ui.state.renameLabel
import com.github.krpkbhdn.ccmanager.ui.state.selectedFile
import com.github.krpkbhdn.ccmanager.ui.state.totalAdditions
import com.github.krpkbhdn.ccmanager.ui.state.totalDeletions
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

private val fileListWidth = 300.dp
private val gutterWidth = 48.dp
private val markerWidth = 20.dp

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
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Label("Changes", weight = FontWeight.SemiBold)
        if (diff != null && diff.gitRepository && diff.files.isNotEmpty()) {
            Label("${diff.files.size} files", color = colors.textSecondary, size = FontSize.small)
            Label("+${totalAdditions(diff)}", color = colors.success, size = FontSize.small, mono = true)
            Label("-${totalDeletions(diff)}", color = colors.danger, size = FontSize.small, mono = true)
        }
        Box(Modifier.weight(1f))
        if (state.loading) Label("Refreshing...", color = colors.textTertiary, size = FontSize.small)
        IconAction(AppIcon.Refresh, onRefresh, enabled = !state.loading)
    }
}

@Composable
private fun DiffBody(state: DiffState) {
    val diff = state.diff
    when {
        state.error != null -> EmptyState(AppIcon.Alert, "Could not read the changes", state.error)
        diff == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Label("Loading changes...", color = colors.textTertiary) }
        !diff.gitRepository -> EmptyState(AppIcon.Folder, "Not a git repository", "Initialise git in this folder to see what the agents changed.")
        diff.files.isEmpty() -> EmptyState(AppIcon.Check, "No changes", "The working tree matches HEAD.")
        else -> DiffContent(diff.files)
    }
}

@Composable
private fun DiffContent(files: List<GitDiff.FileDiff>) {
    var selectedPath by remember { mutableStateOf<String?>(null) }
    val selected = selectedFile(files, selectedPath)
    Row(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.width(fileListWidth).fillMaxHeight(), contentPadding = PaddingValues(Spacing.sm)) {
            items(files, key = { it.path }) { file -> FileRow(file, file == selected) { selectedPath = file.path } }
        }
        VerticalRule()
        selected?.let { FileDiffView(it, Modifier.weight(1f)) }
    }
}

@Composable
private fun changeTint(change: GitDiff.ChangeType): Color = when (change) {
    GitDiff.ChangeType.ADDED, GitDiff.ChangeType.UNTRACKED -> colors.success
    GitDiff.ChangeType.MODIFIED -> colors.warning
    GitDiff.ChangeType.DELETED -> colors.danger
    GitDiff.ChangeType.RENAMED -> colors.accent
}

@Composable
private fun FileRow(file: GitDiff.FileDiff, selected: Boolean, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth(), base = if (selected) colors.surfaceSelected else Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(changeLetter(file.change), Modifier.width(markerWidth), color = changeTint(file.change), weight = FontWeight.Bold, mono = true, size = FontSize.small)
            Column(Modifier.weight(1f)) {
                Label(fileName(file.path), size = FontSize.small, weight = FontWeight.Medium)
                parentDirectory(file.path).takeIf { it.isNotEmpty() }?.let { Label(it, color = colors.textTertiary, size = FontSize.caption) }
            }
            if (!file.binary) {
                Label("+${file.additions}", color = colors.success, size = FontSize.caption, mono = true)
                Label("-${file.deletions}", color = colors.danger, size = FontSize.caption, mono = true)
            }
        }
    }
}

@Composable
private fun FileDiffView(file: GitDiff.FileDiff, modifier: Modifier) {
    Column(modifier.fillMaxHeight()) {
        Label(renameLabel(file), Modifier.padding(horizontal = Spacing.lg, vertical = Spacing.sm), size = FontSize.small, weight = FontWeight.Medium, mono = true)
        HorizontalRule()
        when {
            file.binary -> EmptyState(AppIcon.Diff, "Binary file", "This file cannot be shown as text.")
            file.lines.isEmpty() -> EmptyState(AppIcon.Diff, "No textual changes", "Only the file mode or name changed.")
            else -> LazyColumn(Modifier.fillMaxSize().background(colors.surface)) {
                itemsIndexed(file.lines) { _, line -> DiffLine(line) }
            }
        }
    }
}

@Composable
private fun DiffLine(line: GitDiff.Line) {
    val background = when (line.kind) {
        GitDiff.LineKind.ADDED -> colors.diffAdded
        GitDiff.LineKind.REMOVED -> colors.diffRemoved
        GitDiff.LineKind.HUNK -> colors.diffHunk
        else -> Color.Transparent
    }
    val textColor = when (line.kind) {
        GitDiff.LineKind.HUNK -> colors.accent
        GitDiff.LineKind.META -> colors.textTertiary
        else -> colors.textPrimary
    }
    Row(Modifier.fillMaxWidth().background(background)) {
        Gutter(line.oldNumber)
        Gutter(line.newNumber)
        Label(lineMarker(line.kind), Modifier.width(markerWidth), color = textColor, size = FontSize.small, mono = true)
        Label(line.text, Modifier.weight(1f), color = textColor, size = FontSize.small, mono = true)
    }
}

@Composable
private fun Gutter(number: Int?) {
    Label(lineNumberText(number), Modifier.width(gutterWidth).padding(end = Spacing.sm), color = colors.textTertiary, size = FontSize.small, mono = true, align = TextAlign.End)
}
