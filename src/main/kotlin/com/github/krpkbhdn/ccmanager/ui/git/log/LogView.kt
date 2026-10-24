package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.ContextMenuArea
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import java.time.Instant

private const val PREFETCH_ROWS = 25
private const val MAX_GRAPH_LANES = 10
private val minDetailsWidth = 360.dp
private val maxDetailsWidth = 600.dp
private const val DETAILS_SHARE = 0.5f
private const val SKELETON_ROWS = 10

/** History with graph on the left, the selected commit on the right; pages in as the list scrolls. */
@Composable
fun LogView(git: GitState, now: Instant, modifier: Modifier = Modifier) {
    val log = git.log
    LaunchedEffect(Unit) { if (!log.loaded) log.reload() }
    Column(modifier.fillMaxSize()) {
        LogFilterBar(log.filter, git.branches, log.loading, log::update)
        HorizontalRule()
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val detailsWidth = (maxWidth * DETAILS_SHARE).coerceIn(minDetailsWidth, maxDetailsWidth)
            Row(Modifier.fillMaxSize()) {
                LogList(git, now, Modifier.weight(1f).fillMaxHeight())
                if (log.selected != null) {
                    VerticalRule()
                    CommitDetailsPane(git, now, Modifier.width(detailsWidth).fillMaxHeight())
                }
            }
        }
    }
}

@Composable
private fun LogList(git: GitState, now: Instant, modifier: Modifier) {
    val log = git.log
    val rows = log.rows
    when {
        rows.isEmpty() && log.loading -> Column(modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(SKELETON_ROWS) { SkeletonBlock(Modifier.fillMaxWidth(0.5f + (it % 4) * 0.12f).height(18.dp)) }
        }
        rows.isEmpty() -> EmptyState(Icons.Log, "No commits", if (log.filter.isActive) "Nothing matches these filters." else "This branch has no commits yet.", modifier)
        else -> BoxWithConstraints(modifier) {
            val columns = logColumnsFor(maxWidth.value)
            val laneCount = minOf(rows.maxOf { it.laneCount() }, MAX_GRAPH_LANES)
            val state = rememberLazyListState()
            LaunchedEffect(state, rows.size) {
                snapshotFlow { state.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }.collect { if (it >= rows.size - PREFETCH_ROWS) log.loadMore() }
            }
            LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(horizontal = Spacing.xs, vertical = Spacing.xs)) {
                itemsIndexed(rows, key = { _, row -> row.commit().hash() }) { index, row ->
                    ContextMenuArea({ commitMenu(git, row.commit()) }) {
                        LogRowView(row, rows.getOrNull(index - 1), laneCount, row.commit().hash() == log.selected, columns, now) { log.select(row.commit().hash()) }
                    }
                }
            }
            ThinScrollbar(state)
        }
    }
}

