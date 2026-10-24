package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import com.github.krpkbhdn.ccmanager.ui.git.model.summaryText
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dirtyDot = 6.dp
private val nameMaxWidth = 180.dp

/** The same facts as [GitSummaryChip] as one quiet line, for sidebar rows. */
@Composable
fun GitSummaryLine(summary: GitSummary, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
        Label(summary.branch, Modifier.weight(1f, fill = false), color = colors.textTertiary, style = Type.monoSmall.copy(fontSize = Type.caption.fontSize))
        if (summary.dirty > 0) Label("●${summary.dirty}", color = colors.warning, style = Type.numericCaption)
        if (summary.ahead > 0) Label("↑${summary.ahead}", color = colors.success, style = Type.numericCaption)
        if (summary.behind > 0) Label("↓${summary.behind}", color = colors.warning, style = Type.numericCaption)
    }
}

/** Branch, uncommitted-file count and ahead / behind in one small pill; clicking it opens the Git tab. */
@Composable
fun GitSummaryChip(summary: GitSummary, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.pill)
    WithTooltip(summaryText(summary)) {
        Pressable(onClick, modifier, shape, base = colors.hoverOverlay) {
            Row(Modifier.padding(horizontal = Spacing.sm + 2.dp, vertical = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
                Glyph(Icons.Branch, colors.accent, size = IconSize.medium)
                Label(summary.branch, Modifier.widthIn(max = nameMaxWidth), color = colors.textSecondary, style = Type.monoSmall)
                if (summary.dirty > 0) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    androidx.compose.foundation.layout.Box(Modifier.size(dirtyDot).background(colors.warning, CircleShape))
                    Label(summary.dirty.toString(), color = colors.warning, style = Type.numericCaption)
                }
                if (summary.ahead > 0) Label("↑${summary.ahead}", color = colors.success, style = Type.numericCaption)
                if (summary.behind > 0) Label("↓${summary.behind}", color = colors.warning, style = Type.numericCaption)
            }
        }
    }
}
