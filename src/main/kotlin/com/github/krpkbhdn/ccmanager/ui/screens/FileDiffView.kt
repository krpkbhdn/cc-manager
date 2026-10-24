package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinHorizontalScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.DiffRow
import com.github.krpkbhdn.ccmanager.ui.state.changeBar
import com.github.krpkbhdn.ccmanager.ui.state.diffRows
import com.github.krpkbhdn.ccmanager.ui.state.fileName
import com.github.krpkbhdn.ccmanager.ui.state.lineMarker
import com.github.krpkbhdn.ccmanager.ui.state.lineNumberText
import com.github.krpkbhdn.ccmanager.ui.state.parentDirectory
import com.github.krpkbhdn.ccmanager.ui.state.renameLabel
import com.github.krpkbhdn.ccmanager.ui.state.changeLabel
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

private val gutterWidth = 44.dp
private val markerWidth = 22.dp
private val stripeWidth = 3.dp
private val barBlock = 7.dp
private val barHeight = 8.dp

/** One file's diff: a header with path, change kind and +/- bar, then unified lines with intraline highlights. */
@Composable
fun FileDiffView(file: GitDiff.FileDiff, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxHeight()) {
        FileHeader(file)
        HorizontalRule()
        when {
            file.binary -> EmptyState(Icons.Diff, "Binary file", "This file cannot be shown as text.")
            file.lines.isEmpty() -> EmptyState(Icons.Diff, "No textual changes", "Only the file mode or name changed.")
            else -> DiffLines(file)
        }
    }
}

@Composable
private fun FileHeader(file: GitDiff.FileDiff) {
    val tint = changeTint(file.change)
    Row(Modifier.fillMaxWidth().background(colors.sidebar).padding(horizontal = Spacing.lg, vertical = Spacing.sm + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Glyph(changeIcon(file.change), tint, size = IconSize.large)
        Column(Modifier.weight(1f)) {
            val directory = parentDirectory(file.path)
            Label(if (file.oldPath != null && directory.isEmpty()) renameLabel(file) else fileName(file.path), color = colors.textPrimary, style = Type.monoMedium, tooltipWhenTruncated = true)
            if (directory.isNotEmpty()) Label(directory, color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
        }
        Chip(changeLabel(file.change), tint)
        if (!file.binary) ChangeBar(file.additions, file.deletions)
        IconButton(Icons.Copy, { copyToClipboard(file.path) }, tooltip = "Copy path", size = 24.dp)
    }
}

@Composable
private fun ChangeBar(additions: Int, deletions: Int) {
    val (added, removed, neutral) = changeBar(additions, deletions)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("+$additions", color = colors.success, style = Type.numeric)
        Label("-$deletions", color = colors.danger, style = Type.numeric)
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            repeat(added) { Block(colors.success) }
            repeat(removed) { Block(colors.danger) }
            repeat(neutral) { Block(colors.borderStrong) }
        }
    }
}

@Composable
private fun Block(color: Color) {
    Box(Modifier.width(barBlock).height(barHeight).background(color, RoundedCornerShape(2.dp)))
}

@Composable
private fun DiffLines(file: GitDiff.FileDiff) {
    val rows = remember(file) { diffRows(file.lines) }
    val vertical = rememberLazyListState()
    val horizontal = rememberScrollState()
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.terminal)) {
        val viewport = maxWidth
        LazyColumn(Modifier.fillMaxSize(), vertical) {
            items(rows) { row -> DiffLine(row, viewport, horizontal) }
        }
        ThinScrollbar(vertical)
        ThinHorizontalScrollbar(horizontal)
    }
}

@Composable
private fun DiffLine(row: DiffRow, viewport: androidx.compose.ui.unit.Dp, horizontal: androidx.compose.foundation.ScrollState) {
    val line = row.line
    val background = when (line.kind) {
        GitDiff.LineKind.ADDED -> colors.diffAdded
        GitDiff.LineKind.REMOVED -> colors.diffRemoved
        GitDiff.LineKind.HUNK -> colors.diffHunk
        else -> Color.Transparent
    }
    val stripe = when (line.kind) {
        GitDiff.LineKind.ADDED -> colors.success
        GitDiff.LineKind.REMOVED -> colors.danger
        else -> Color.Transparent
    }
    val strong = if (line.kind == GitDiff.LineKind.ADDED) colors.diffAddedStrong else colors.diffRemovedStrong
    val textColor = when (line.kind) {
        GitDiff.LineKind.HUNK -> colors.accent
        GitDiff.LineKind.META -> colors.textTertiary
        else -> colors.textPrimary
    }
    Box(Modifier.fillMaxWidth().background(background)) {
        Box(Modifier.width(stripeWidth).height(20.dp).background(stripe))
        Row(Modifier.horizontalScroll(horizontal).widthIn(min = viewport).padding(start = stripeWidth), verticalAlignment = Alignment.CenterVertically) {
            Gutter(line.oldNumber)
            Gutter(line.newNumber)
            Label(lineMarker(line.kind), Modifier.width(markerWidth), color = if (stripe == Color.Transparent) colors.textTertiary else stripe, style = Type.monoBody, align = TextAlign.Center)
            BasicText(highlighted(line.text, row.highlight, strong), style = Type.monoBody.copy(color = textColor), maxLines = 1, softWrap = false)
        }
    }
}

private fun highlighted(text: String, range: IntRange?, color: Color): AnnotatedString =
    buildAnnotatedString {
        append(text)
        range?.let { addStyle(SpanStyle(background = color), it.first, it.last + 1) }
    }

@Composable
private fun Gutter(number: Int?) {
    Label(lineNumberText(number), Modifier.width(gutterWidth).padding(end = Spacing.sm), color = colors.textTertiary, style = Type.monoSmall, align = TextAlign.End)
}
