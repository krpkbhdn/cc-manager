package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.ThinHorizontalScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.diffRows
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

private val loadingSpinner = 20.dp

/** What the pane may do with the lines it shows: staging actions exist only outside [READ_ONLY]. */
enum class DiffContext { UNSTAGED, STAGED, READ_ONLY }

/** Hunk-level actions; for a staged diff both mean "unstage". */
class HunkActions(
    val onApplyHunk: (Hunk) -> Unit,
    val onApplyLines: (Hunk, Set<Int>) -> Unit,
)

/** One file's diff with a header; hunk headers carry the staging buttons and change lines a checkbox for partial staging. */
@Composable
fun GitDiffPane(
    file: GitDiff.FileDiff?,
    context: DiffContext,
    modifier: Modifier = Modifier,
    actions: HunkActions? = null,
    loading: Boolean = false,
    busy: Boolean = false,
) {
    Column(modifier.fillMaxHeight()) {
        when {
            file == null && loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Spinner(size = loadingSpinner) }
            file == null -> EmptyState(Icons.Diff, "Select a file", "Pick a file on the left to review its changes.")
            else -> {
                DiffFileHeader(file, context)
                HorizontalRule()
                when {
                    file.binary -> EmptyState(Icons.Diff, "Binary file", "This file cannot be shown as text.")
                    file.lines.isEmpty() -> EmptyState(Icons.Diff, "No textual changes", "Only the file mode or name changed.")
                    else -> DiffBody(file, context, actions, busy)
                }
            }
        }
    }
}

@Composable
private fun DiffBody(file: GitDiff.FileDiff, context: DiffContext, actions: HunkActions?, busy: Boolean) {
    val rows = remember(file) { diffRows(file.lines) }
    val hunkAt = remember(file) { hunksOf(file).associateBy { it.header } }
    val picked = remember(file) { mutableStateListOf<Int>() }
    val vertical = rememberLazyListState()
    val horizontal = rememberScrollState()
    val editable = context != DiffContext.READ_ONLY && actions != null
    BoxWithConstraints(Modifier.fillMaxSize().background(colors.terminal)) {
        val viewport = maxWidth
        LazyColumn(Modifier.fillMaxSize(), vertical) {
            itemsIndexed(rows) { index, row ->
                val hunk = hunkAt[index]
                when {
                    hunk != null -> HunkHeaderRow(row.line.text, if (editable) HunkControls(hunk, context, picked.count { it in hunk.body }, busy, actions, picked) else null)
                    else -> {
                        val pick = if (editable && isChangeLine(row.line)) LinePick(index in picked) { if (index in picked) picked.remove(index) else picked.add(index) } else null
                        DiffLineRow(row, viewport, horizontal, pick, showPicks = editable)
                    }
                }
            }
        }
        ThinScrollbar(vertical)
        ThinHorizontalScrollbar(horizontal)
    }
}
