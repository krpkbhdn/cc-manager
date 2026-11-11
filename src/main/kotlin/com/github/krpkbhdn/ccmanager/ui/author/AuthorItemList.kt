package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val DESCRIPTION_LINES = 3
private val checkColumn = 20.dp

/** The right pane: a header per group followed by its item cards, scrolling to the group picked in the middle pane. */
@Composable
fun AuthorItemList(view: CatalogView, enabled: Boolean) {
    val rows = CatalogSelection.rows(view.groups)
    val installed = CatalogSelection.installedIds(view.entries)
    val list = rememberLazyListState()
    LaunchedEffect(view.focusedGroup) {
        val key = view.focusedGroup ?: return@LaunchedEffect
        val index = rows.indexOfFirst { it is CatalogRow.Header && it.group.key == key }
        if (index >= 0) list.animateScrollToItem(index)
        view.focusedGroup = null
    }
    Box(Modifier.fillMaxSize()) {
        if (rows.isEmpty()) {
            Label(CatalogSelection.emptyMessage(view.searching, view.onlyNew), Modifier.padding(Spacing.xl), color = colors.textTertiary, style = Type.small, maxLines = 3)
        }
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            items(rows, key = { row -> if (row is CatalogRow.Header) "h:${row.group.key}" else (row as CatalogRow.Card).entry.item().id() }) { row ->
                when (row) {
                    is CatalogRow.Header -> GroupHeader(row.group, view.selected)
                    is CatalogRow.Card -> ItemCard(view, row.entry, installed, enabled)
                }
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun GroupHeader(group: CatalogGroup, selected: Set<String>) {
    Row(Modifier.fillMaxWidth().padding(top = Spacing.xs, start = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(group.title, color = colors.textSecondary, style = Type.smallMedium)
        CountBadge(group.entries.count { it.item().id() in selected })
        Label("of ${group.entries.size}", color = colors.textTertiary, style = Type.caption)
    }
}

@Composable
private fun ItemCard(view: CatalogView, entry: AuthorEntry, installed: Set<String>, enabled: Boolean) {
    val item = entry.item()
    val selectable = CatalogSelection.selectable(entry)
    val checked = item.id() in view.selected
    val shape = RoundedCornerShape(Radius.md)
    val also = CatalogSelection.alsoInstalls(view.catalog, item.id(), installed)
    Pressable(
        { view.check(item.id(), !checked) },
        Modifier.fillMaxWidth().alpha(if (enabled) 1f else Alpha.DISABLED).border(1.dp, if (checked) colors.accent.copy(alpha = Alpha.BORDER_TINT) else colors.border, shape),
        shape = shape, base = if (checked) colors.accentSoft.copy(alpha = Alpha.FAINT * 2) else colors.surface, hover = if (checked) null else colors.surfaceHover, enabled = enabled && selectable,
    ) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.Top) {
            Box(Modifier.padding(top = Spacing.xxs).width(checkColumn), contentAlignment = Alignment.Center) {
                if (selectable) AppCheckbox(checked, { view.check(item.id(), it) }, enabled = enabled)
                else Glyph(Icons.Check, colors.textTertiary, size = 14.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Label(item.title(), Modifier.weight(1f, fill = false), style = Type.bodyMedium, color = if (selectable) colors.textPrimary else colors.textSecondary)
                    StatusMark(entry.status())
                }
                Label(item.description(), color = colors.textSecondary, style = Type.small, maxLines = DESCRIPTION_LINES)
                if (selectable && also.isNotEmpty()) Label("Also installs: ${also.joinToString()}", color = colors.textTertiary, style = Type.caption, maxLines = 2)
                if (replacesEdits(entry) && selectable) Label(TEAM_REPLACE_WARNING, color = colors.warning, style = Type.caption, maxLines = 3)
                ChangeNotes(entry)
            }
        }
    }
}

@Composable
private fun StatusMark(status: AuthorStatus) {
    when (status) {
        AuthorStatus.UPDATE_AVAILABLE -> Chip(statusLabel(status), colors.warning)
        AuthorStatus.DECLINED -> Chip(statusLabel(status), colors.textTertiary)
        AuthorStatus.INSTALLED -> Label(statusLabel(status), color = colors.textTertiary, style = Type.caption)
        AuthorStatus.NOT_INSTALLED -> Unit
    }
}

@Composable
private fun ChangeNotes(entry: AuthorEntry) {
    if (entry.status() != AuthorStatus.UPDATE_AVAILABLE) return
    entry.changes().forEach { Label("- $it", color = colors.textSecondary, style = Type.caption, maxLines = 2) }
}
