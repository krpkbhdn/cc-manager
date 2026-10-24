package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.copyToClipboard
import com.github.krpkbhdn.ccmanager.ui.git.kindOf
import com.github.krpkbhdn.ccmanager.ui.git.kindTint
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.DiffRow
import com.github.krpkbhdn.ccmanager.ui.state.changeBar
import com.github.krpkbhdn.ccmanager.ui.state.changeLabel
import com.github.krpkbhdn.ccmanager.ui.state.fileName
import com.github.krpkbhdn.ccmanager.ui.state.lineMarker
import com.github.krpkbhdn.ccmanager.ui.state.lineNumberText
import com.github.krpkbhdn.ccmanager.ui.state.parentDirectory
import com.github.krpkbhdn.ccmanager.ui.state.renameLabel
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

private val gutterWidth = 44.dp
private val markerWidth = 22.dp
private val pickWidth = 24.dp
private val stripeWidth = 3.dp
private val barBlock = 7.dp
private val barHeight = 8.dp
private val lineHeight = 20.dp
private val hunkHeight = 30.dp
private val pickBox = 13.dp
private const val BAR_GAP = 2

/** The editable state of one hunk header: how many of its lines are ticked and what the buttons do. */
internal class HunkControls(
    val hunk: Hunk,
    val context: DiffContext,
    val selectedCount: Int,
    val busy: Boolean,
    val actions: HunkActions,
    val picked: MutableList<Int>,
)

internal class LinePick(val checked: Boolean, val onToggle: () -> Unit)

@Composable
internal fun DiffFileHeader(file: GitDiff.FileDiff, context: DiffContext) {
    val kind = kindOf(file.change)
    val tint = kindTint(kind)
    val directory = parentDirectory(file.path)
    Column(
        Modifier.fillMaxWidth().background(colors.sidebar).padding(horizontal = Spacing.lg, vertical = Spacing.sm + 2.dp),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Column(Modifier.weight(1f)) {
                Label(if (file.oldPath != null && directory.isEmpty()) renameLabel(file) else fileName(file.path), color = colors.textPrimary, style = Type.monoMedium, tooltipWhenTruncated = true)
                if (directory.isNotEmpty()) Label(directory, color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
            }
            IconButton(Icons.Copy, { copyToClipboard(file.path) }, tooltip = "Copy path", size = 24.dp)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Chip(changeLabel(file.change), tint)
            if (context != DiffContext.READ_ONLY) Chip(if (context == DiffContext.STAGED) "Staged" else "Working tree", colors.textSecondary)
            Box(Modifier.weight(1f))
            if (!file.binary) ChangeBar(file.additions, file.deletions)
        }
    }
}

@Composable
private fun ChangeBar(additions: Int, deletions: Int) {
    val (added, removed, neutral) = changeBar(additions, deletions)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label("+$additions", color = colors.success, style = Type.numeric)
        Label("-$deletions", color = colors.danger, style = Type.numeric)
        Row(horizontalArrangement = Arrangement.spacedBy(BAR_GAP.dp)) {
            repeat(added) { Block(colors.success) }
            repeat(removed) { Block(colors.danger) }
            repeat(neutral) { Block(colors.borderStrong) }
        }
    }
}

@Composable
private fun Block(color: Color) {
    Box(Modifier.width(barBlock).height(barHeight).background(color, RoundedCornerShape(BAR_GAP.dp)))
}

@Composable
internal fun HunkHeaderRow(text: String, controls: HunkControls?) {
    Row(
        Modifier.fillMaxWidth().height(hunkHeight).background(colors.diffHunk).padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Label(text, Modifier.weight(1f), color = colors.accent, style = Type.monoSmall, tooltipWhenTruncated = true)
        controls?.let { HunkButtons(it) }
    }
}

@Composable
private fun HunkButtons(controls: HunkControls) {
    val staged = controls.context == DiffContext.STAGED
    val verb = if (staged) "Unstage" else "Stage"
    val anyPicked = controls.selectedCount > 0
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        if (anyPicked) {
            AppButton("$verb selected (${controls.selectedCount})", { controls.actions.onApplyLines(controls.hunk, controls.picked.toSet()); controls.picked.clear() }, variant = ButtonVariant.PRIMARY, compact = true, enabled = !controls.busy)
        } else {
            AppButton("$verb hunk", { controls.actions.onApplyHunk(controls.hunk) }, compact = true, enabled = !controls.busy)
        }
    }
}

@Composable
internal fun DiffLineRow(row: DiffRow, viewport: Dp, horizontal: ScrollState, pick: LinePick?, showPicks: Boolean) {
    val line = row.line
    val background = when (line.kind) {
        GitDiff.LineKind.ADDED -> colors.diffAdded
        GitDiff.LineKind.REMOVED -> colors.diffRemoved
        else -> Color.Transparent
    }
    val stripe = when (line.kind) {
        GitDiff.LineKind.ADDED -> colors.success
        GitDiff.LineKind.REMOVED -> colors.danger
        else -> Color.Transparent
    }
    val strong = if (line.kind == GitDiff.LineKind.ADDED) colors.diffAddedStrong else colors.diffRemovedStrong
    val textColor = if (line.kind == GitDiff.LineKind.META) colors.textTertiary else colors.textPrimary
    Box(Modifier.fillMaxWidth().background(background)) {
        Box(Modifier.width(stripeWidth).height(lineHeight).background(stripe))
        Row(Modifier.horizontalScroll(horizontal).widthIn(min = viewport).padding(start = stripeWidth), verticalAlignment = Alignment.CenterVertically) {
            if (showPicks) PickCell(pick)
            Gutter(line.oldNumber)
            Gutter(line.newNumber)
            Label(lineMarker(line.kind), Modifier.width(markerWidth), color = if (stripe == Color.Transparent) colors.textTertiary else stripe, style = Type.monoBody, align = TextAlign.Center)
            BasicText(highlighted(line.text, row.highlight, strong), style = Type.monoBody.copy(color = textColor), maxLines = 1, softWrap = false)
        }
    }
}

@Composable
private fun PickCell(pick: LinePick?) {
    Box(Modifier.width(pickWidth).height(lineHeight), contentAlignment = Alignment.Center) {
        if (pick != null) AppCheckbox(pick.checked, { pick.onToggle() }, boxSize = pickBox)
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
