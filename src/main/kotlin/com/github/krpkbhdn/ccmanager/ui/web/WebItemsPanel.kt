package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.review.ReviewStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.review.toolIcon
import com.github.krpkbhdn.ccmanager.ui.review.toolName
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.web.WebItem
import com.github.krpkbhdn.ccmanager.web.WebItemStatus
import com.github.krpkbhdn.ccmanager.web.WebRevision

private val badgeSize = 22.dp

/** Revisions newest first, each with its items grouped by page; a row jumps to the item's pin in the page. */
@Composable
fun WebItemsPanel(projectId: Long, view: WebView, state: WebState) {
    val revisions = state.revisions(projectId)
    if (revisions.isEmpty()) {
        EmptyState(Icons.Message, "No items yet", "Pick an element, describe the change and add it to the review.")
        return
    }
    var toggled by remember { mutableStateOf(emptySet<Long>()) }
    val scope = view.device.scope
    val rows = itemRows(revisions, state::items, state.pages(projectId), toggled, scope)
    val list = rememberLazyListState()
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), list, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            items(rows, key = ItemsRow::key) { row ->
                when (row) {
                    is ItemsRow.Header -> RevisionHeader(row, projectId, state) { id -> toggled = if (id in toggled) toggled - id else toggled + id }
                    is ItemsRow.PageHeading -> Label(row.title, Modifier.padding(horizontal = Spacing.md), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
                    is ItemsRow.Entry -> ItemRow(row.item, row.item.id() == view.selectedItemId, state, view)
                }
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun RevisionHeader(row: ItemsRow.Header, projectId: Long, state: WebState, onToggle: (Long) -> Unit) {
    val revision = row.revision
    Column(Modifier.fillMaxWidth().clickable { onToggle(revision.id()) }.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(if (row.expanded) Icons.ChevronDown else Icons.ChevronRight, colors.textSecondary, size = 14.dp)
            Label("Revision ${revision.number()}", style = Type.bodyMedium)
            RevisionStatus(revision.status())
            val shownNote = if (row.shown < row.total) " · ${row.shown} shown on this device" else ""
            Label("${row.openCount} open of ${row.total}$shownNote", Modifier.weight(1f), color = colors.textTertiary, style = Type.caption)
        }
        RevisionActions(row, projectId, state)
    }
}

@Composable
private fun RevisionActions(row: ItemsRow.Header, projectId: Long, state: WebState) {
    val revision = row.revision
    if (revision.status() == ReviewStatus.APPROVED && !row.startsNext) return
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        when (revision.status()) {
            ReviewStatus.PENDING -> AppButton("Send to agent", { state.send(revision) }, variant = ButtonVariant.PRIMARY, icon = Icons.Send, enabled = row.openCount > 0 && !state.isSending(revision.id()), loading = state.isSending(revision.id()), compact = true)
            ReviewStatus.SENT -> AppButton("Approve", { state.approve(revision) }, icon = Icons.Check, compact = true)
            ReviewStatus.APPROVED -> Unit
        }
        if (row.startsNext) AppButton("Next revision", { state.startNextRevision(projectId) }, icon = Icons.Plus, compact = true)
    }
}

@Composable
private fun RevisionStatus(status: ReviewStatus) = when (status) {
    ReviewStatus.PENDING -> Chip("Pending", colors.warning)
    ReviewStatus.SENT -> Chip("Sent", colors.accent)
    ReviewStatus.APPROVED -> Chip("Approved", colors.success)
}

@Composable
private fun ItemRow(item: WebItem, selected: Boolean, state: WebState, view: WebView) {
    val shape = RoundedCornerShape(Radius.md)
    val tint = statusTint(item.status())
    val focus = remember { FocusRequester() }
    Column(
        Modifier.padding(horizontal = Spacing.md).fillMaxWidth()
            .background(if (selected) colors.accentSoft else colors.surface, shape)
            .border(1.dp, if (selected) colors.accent else colors.border, shape)
            .clip(shape)
            .focusRequester(focus)
            .onKeyEvent { it.type == KeyEventType.KeyDown && state.deleteOnKey(view, item, it.key, it.isCtrlPressed || it.isShiftPressed || it.isAltPressed || it.isMetaPressed) }
            .focusable()
            .clickable { state.selectItem(view, item); focus.requestFocus() }.padding(Spacing.sm),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Box(Modifier.size(badgeSize).background(tint, CircleShape), contentAlignment = Alignment.Center) {
                Label(item.n().toString(), color = colors.onAccent, style = Type.numericCaption)
            }
            Chip(item.status().name.lowercase().replaceFirstChar(Char::titlecase), tint)
            item.viewport()?.let { Chip(viewportLabel(it), colors.neutral) }
            val markTool = item.shape()?.kind?.tool()
            if (markTool != null) {
                Glyph(toolIcon(markTool), colors.textTertiary, size = 14.dp)
                Label(toolName(markTool), Modifier.weight(1f), color = colors.textTertiary, style = Type.monoSmall)
            } else {
                Label(item.anchor().tag().orEmpty(), Modifier.weight(1f), color = colors.textTertiary, style = Type.monoSmall)
            }
            ItemActions(item, state, view)
        }
        Label(item.comment().ifBlank { "No comment" }, color = if (item.comment().isBlank()) colors.textTertiary else colors.textPrimary, maxLines = 3)
        if (item.shape() == null) Label(item.anchor().selector(), color = colors.textTertiary, style = Type.monoSmall, tooltipWhenTruncated = true)
    }
}

@Composable
private fun ItemActions(item: WebItem, state: WebState, view: WebView) {
    if (item.status() == WebItemStatus.OPEN) {
        IconButton(Icons.Edit, { state.editItem(view, item) }, tooltip = "Edit")
        IconButton(Icons.Close, { state.setStatus(item, WebItemStatus.DISMISSED) }, tooltip = "Dismiss")
    } else {
        IconButton(Icons.Undo, { state.setStatus(item, WebItemStatus.OPEN) }, tooltip = "Reopen")
    }
    IconButton(Icons.Trash, { state.delete(item) }, tooltip = "Delete", tint = colors.danger)
}

@Composable
private fun statusTint(status: WebItemStatus): Color = when (status) {
    WebItemStatus.OPEN -> colors.warning
    WebItemStatus.SENT -> colors.accent
    WebItemStatus.DONE -> colors.success
    WebItemStatus.DISMISSED -> colors.neutral
}
