package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.git.model.absoluteDate
import com.github.krpkbhdn.ccmanager.ui.git.model.commitAge
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant

val LogRowHeight = 30.dp
private val authorWidth = 120.dp
private val dateWidth = 76.dp
private val hashWidth = 64.dp
private val chipsMaxWidth = 260.dp
private const val WIDE_CHIP_LIMIT = 2
private const val NARROW_CHIP_LIMIT = 1

/** Which of the trailing columns fit; the graph and subject always show. */
data class LogColumns(val date: Boolean, val author: Boolean, val hash: Boolean)

fun logColumnsFor(widthDp: Float): LogColumns = LogColumns(date = widthDp >= DATE_MIN_WIDTH, author = widthDp >= AUTHOR_MIN_WIDTH, hash = widthDp >= HASH_MIN_WIDTH)

private const val DATE_MIN_WIDTH = 420f
private const val AUTHOR_MIN_WIDTH = 600f
private const val HASH_MIN_WIDTH = 760f

/** One history line: graph, ref chips and subject, author, age and short hash; cheap enough for thousands of rows. */
@Composable
fun LogRowView(row: GitLog.Row, previous: GitLog.Row?, laneCount: Int, selected: Boolean, columns: LogColumns, now: () -> Instant, onClick: () -> Unit) {
    val commit = row.commit()
    val laneColor = graphColor(row.color(), colors.dark)
    Pressable(onClick, Modifier.fillMaxWidth(), RoundedCornerShape(Radius.sm), base = if (selected) colors.accentSoft else Color.Transparent) {
        Row(Modifier.height(LogRowHeight).padding(end = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            GraphCell(row, previous, laneCount, LogRowHeight)
            RefChips(commit.refs(), laneColor, Modifier.weight(1f, fill = false).widthIn(max = chipsMaxWidth).padding(end = Spacing.sm), limit = if (columns.date) WIDE_CHIP_LIMIT else NARROW_CHIP_LIMIT)
            Label(commit.subject(), Modifier.weight(1f), color = colors.textPrimary, style = Type.small, tooltipWhenTruncated = true)
            if (columns.author) Label(commit.authorName(), Modifier.width(authorWidth).padding(start = Spacing.sm), color = colors.textSecondary, style = Type.small)
            if (columns.date) WithTooltip(absoluteDate(commit.authorDate())) {
                Label(commitAge(commit.authorDate(), now()), Modifier.width(dateWidth).padding(start = Spacing.sm), color = colors.textTertiary, style = Type.small)
            }
            if (columns.hash) Label(commit.shortHash(), Modifier.width(hashWidth), color = colors.textTertiary, style = Type.monoSmall)
        }
    }
}
