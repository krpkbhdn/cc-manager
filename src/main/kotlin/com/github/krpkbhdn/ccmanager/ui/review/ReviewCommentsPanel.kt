package com.github.krpkbhdn.ccmanager.ui.review

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.review.MarkDraft
import com.github.krpkbhdn.ccmanager.review.MarkKind
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.ui.voice.VoiceTextArea

private val badgeSize = 22.dp
private const val COMMENT_MIN_LINES = 2
private const val COMMENT_MAX_LINES = 6

/** Numbered comments of the current image; a row selects its mark on the canvas. */
@Composable
fun ReviewCommentsPanel(
    imageId: Long,
    marks: List<MarkDraft>,
    selected: Int?,
    onSelect: (Int) -> Unit,
    onComment: (Int, String) -> Unit,
    onDelete: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Label("Comments", Modifier.padding(Spacing.md), style = Type.bodyMedium)
        if (marks.isEmpty()) {
            EmptyState(Icons.Message, "No marks yet", "Pick a tool and mark what is wrong, then describe it here.")
            return@Column
        }
        val list = rememberLazyListState()
        Box(Modifier.weight(1f)) {
            LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                itemsIndexed(marks, key = { index, _ -> "$imageId-${marks.size}-$index" }) { index, mark ->
                    CommentRow(index, mark, index == selected, { onSelect(index) }, { onComment(index, it) }, { onDelete(index) })
                }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun CommentRow(index: Int, mark: MarkDraft, selected: Boolean, onSelect: () -> Unit, onComment: (String) -> Unit, onDelete: () -> Unit) = key(index) {
    val text = rememberTextFieldState(mark.comment)
    val current = rememberUpdatedState(mark.comment)
    LaunchedEffect(text) { snapshotFlow { text.text.toString() }.collect { if (it != current.value) onComment(it) } }
    val shape = RoundedCornerShape(Radius.md)
    Column(
        Modifier.padding(horizontal = Spacing.sm).fillMaxWidth()
            .background(if (selected) colors.accentSoft else colors.surface, shape)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clip(shape).clickable(onClick = onSelect).padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(badgeSize).background(MarkColor, CircleShape), contentAlignment = Alignment.Center) {
                Label(MarkGeometry.labelOf(index), color = Color.White, style = Type.numericCaption)
            }
            Glyph(kindIcon(mark.kind), colors.textSecondary, size = 14.dp)
            Label(kindName(mark.kind), Modifier.weight(1f), color = colors.textSecondary, style = Type.small)
            IconButton(Icons.Trash, onDelete, tooltip = "Delete mark", tint = colors.danger)
        }
        VoiceTextArea(text, Modifier.fillMaxWidth(), placeholder = "What should change here?", minLines = COMMENT_MIN_LINES, maxLines = COMMENT_MAX_LINES)
    }
}

private fun kindIcon(kind: MarkKind) = when (kind) {
    MarkKind.RECTANGLE -> Icons.Square
    MarkKind.ELLIPSE -> Icons.Circle
    MarkKind.PIN -> Icons.MapPin
    MarkKind.FREEFORM -> Icons.PenTool
}

private fun kindName(kind: MarkKind) = when (kind) {
    MarkKind.RECTANGLE -> "Rectangle"
    MarkKind.ELLIPSE -> "Ellipse"
    MarkKind.PIN -> "Pin"
    MarkKind.FREEFORM -> "Freeform"
}
