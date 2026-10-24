package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.ui.components.CopyChip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.git.KindBadge
import com.github.krpkbhdn.ccmanager.ui.git.changes.DiffContext
import com.github.krpkbhdn.ccmanager.ui.git.changes.GitDiffPane
import com.github.krpkbhdn.ccmanager.ui.git.kindOf
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.absoluteDate
import com.github.krpkbhdn.ccmanager.ui.git.model.commitAge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.fileName
import com.github.krpkbhdn.ccmanager.ui.state.parentDirectory
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.time.Instant

private val headerMaxHeight = 230.dp
private val filesMaxHeight = 150.dp
private val fileRowHeight = 26.dp

/** The selected commit: message, author, hash, parents as links, changed files and the diff of the chosen file. */
@Composable
fun CommitDetailsPane(git: GitState, now: Instant, modifier: Modifier = Modifier) {
    val log = git.log
    val details = log.details?.takeIf { it.commit().hash() == log.selected }
    when {
        log.selected == null -> EmptyState(Icons.Commit, "Select a commit", "Its message, files and diff show up here.", modifier)
        details == null -> Column(modifier.fillMaxHeight().fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) { Spinner(size = 20.dp) }
        else -> Column(modifier.fillMaxHeight()) {
            CommitHeader(details.commit(), now) { git.log.select(it) }
            HorizontalRule()
            DiffFileList(details.files(), log.selectedFile) { log.selectedFile = it }
            HorizontalRule()
            GitDiffPane(details.files().firstOrNull { it.path == log.selectedFile }, DiffContext.READ_ONLY, Modifier.weight(1f))
        }
    }
}

@Composable
private fun CommitHeader(commit: GitLog.Commit, now: Instant, onParent: (String) -> Unit) {
    Column(Modifier.heightIn(max = headerMaxHeight).verticalScroll(rememberScrollState()).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(commit.subject(), color = colors.textPrimary, style = Type.heading, maxLines = 3)
        if (commit.body().orEmpty().isNotBlank()) Label(commit.body().orEmpty().trim(), color = colors.textSecondary, style = Type.small, maxLines = 12)
        RefChips(commit.refs(), colors.accent)
        Label("${commit.authorName()}  <${commit.authorEmail()}>", color = colors.textSecondary, style = Type.small, tooltipWhenTruncated = true)
        Label("${absoluteDate(commit.authorDate())}  ·  ${commitAge(commit.authorDate(), now)}", color = colors.textTertiary, style = Type.small)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            CopyChip(commit.shortHash(), copyText = commit.hash(), label = "Copy full hash")
            commit.parents().forEach { parent -> ParentLink(parent.take(SHORT_PARENT)) { onParent(parent) } }
        }
    }
}

private const val SHORT_PARENT = 7

@Composable
private fun ParentLink(short: String, onClick: () -> Unit) {
    Row(Modifier.pointerHoverIcon(PointerIcon.Hand).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label("parent", color = colors.textTertiary, style = Type.caption)
        Label(short, color = colors.accent, style = Type.monoSmall)
    }
}

@Composable
internal fun DiffFileList(files: List<GitDiff.FileDiff>, selected: String?, onSelect: (String) -> Unit) {
    LazyColumn(Modifier.heightIn(max = filesMaxHeight).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.xs)) {
        items(files, key = { it.path }) { file ->
            Pressable({ onSelect(file.path) }, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm), base = if (file.path == selected) colors.accentSoft else Color.Transparent) {
                Row(Modifier.padding(horizontal = Spacing.sm).heightIn(min = fileRowHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    KindBadge(kindOf(file.change))
                    Label(fileName(file.path), color = colors.textPrimary, style = Type.smallMedium)
                    Label(parentDirectory(file.path), Modifier.weight(1f), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
                    if (!file.binary) {
                        if (file.additions > 0) Label("+${file.additions}", color = colors.success, style = Type.numericCaption)
                        if (file.deletions > 0) Label("-${file.deletions}", color = colors.danger, style = Type.numericCaption)
                    }
                }
            }
        }
    }
}
