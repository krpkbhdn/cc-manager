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
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.git.model.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.state.DiffRow
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
    review: ReviewEditing? = null,
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
                    else -> DiffBody(file, context, actions, busy, review)
                }
            }
        }
    }
}

@Composable
private fun DiffBody(file: GitDiff.FileDiff, context: DiffContext, actions: HunkActions?, busy: Boolean, review: ReviewEditing?) {
    val rows = remember(file) { diffRows(file.lines) }
    val hunks = remember(file) { hunksOf(file) }
    val hunkAt = remember(file) { hunks.associateBy { it.header } }
    val picked = remember(file) { mutableStateListOf<Int>() }
    var selection by remember(file.path) { mutableStateOf<IntRange?>(null) }
    val draft = remember(file.path) { TextFieldState() }
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
                        val lineReview = review?.let { reviewOf(it, index, row, selection, hunks, rows) { range -> selection = range } }
                        Column {
                            DiffLineRow(row, viewport, horizontal, pick, showPicks = editable, review = lineReview, showReview = review != null)
                            if (review != null && selection?.last == index) CommentEditor(draft, { review.onAdd(selection!!, it); draft.clearText(); selection = null }, { draft.clearText(); selection = null })
                        }
                    }
                }
            }
        }
        ThinScrollbar(vertical)
        ThinHorizontalScrollbar(horizontal)
    }
}

private fun reviewOf(review: ReviewEditing, index: Int, row: DiffRow, selection: IntRange?, hunks: List<Hunk>, rows: List<DiffRow>, select: (IntRange?) -> Unit): LineReview {
    val covering = review.comments.filter { it.covers(row.line) }
    val status = covering.minByOrNull { it.status == ReviewStatus.STALE }?.status
    val selectable = { at: Int -> rows[at].line.kind != GitDiff.LineKind.META }
    return LineReview(selection?.let { index in it } == true, status) { extend -> select(extendSelection(selection, index, extend, hunks, selectable)) }
}
