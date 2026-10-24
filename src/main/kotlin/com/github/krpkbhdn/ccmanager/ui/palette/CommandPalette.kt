package com.github.krpkbhdn.ccmanager.ui.palette

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Kbd
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.Shortcut
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.components.popIn
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.fuzzyFilter
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val paletteWidth = 620.dp
private val listMaxHeight = 380.dp
private val topOffset = 96.dp
private val leadingBox = 28.dp

private sealed interface PaletteRow {
    data class Heading(val group: PaletteGroup) : PaletteRow
    data class Entry(val item: PaletteItem) : PaletteRow
}

private fun rowsOf(items: List<PaletteItem>): List<PaletteRow> =
    PaletteGroup.entries.flatMap { group ->
        val inGroup = items.filter { it.group == group }
        if (inGroup.isEmpty()) emptyList() else listOf<PaletteRow>(PaletteRow.Heading(group)) + inGroup.map { PaletteRow.Entry(it) }
    }

/** Command palette: fuzzy jump to sessions and projects, or run an action; arrows move, Enter runs, Esc closes. */
@Composable
fun CommandPalette(items: List<PaletteItem>, onDismiss: () -> Unit) {
    val query: TextFieldState = rememberTextFieldState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    val matches = remember(items, query.text.toString()) { fuzzyFilter(query.text.toString(), items) { "${it.title} ${it.subtitle}" } }
    val rows = remember(matches) { rowsOf(matches) }
    var selected by remember(matches) { mutableIntStateOf(0) }
    val list = rememberLazyListState()
    var mouseActive by remember { mutableStateOf(false) }
    val run = { matches.getOrNull(selected)?.let { onDismiss(); it.run() }; Unit }
    LaunchedEffect(selected, rows) {
        val rowIndex = rows.indexOfFirst { it is PaletteRow.Entry && it.item == matches.getOrNull(selected) }
        val visible = list.layoutInfo.visibleItemsInfo.any { it.index == rowIndex }
        if (rowIndex >= 0 && !visible) list.animateScrollToItem((rowIndex - 2).coerceAtLeast(0))
    }
    Box(
        Modifier.fillMaxSize().background(colors.scrim).clickable(remember { MutableInteractionSource() }, null, onClick = onDismiss)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) if (awaitPointerEvent().type == PointerEventType.Move) mouseActive = true
                }
            }
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when (event.key) {
                    Key.Escape -> { onDismiss(); true }
                    Key.DirectionDown -> { if (matches.isNotEmpty()) selected = (selected + 1) % matches.size; true }
                    Key.DirectionUp -> { if (matches.isNotEmpty()) selected = (selected - 1 + matches.size) % matches.size; true }
                    Key.Enter, Key.NumPadEnter -> { run(); true }
                    else -> false
                }
            },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            Modifier.padding(top = topOffset).popIn(TransformOrigin(0.5f, 0f)).width(paletteWidth)
                .card(RoundedCornerShape(Radius.xl), colors.surfaceRaised, raised = true)
                .clickable(remember { MutableInteractionSource() }, null) {},
        ) {
            Box(Modifier.padding(Spacing.md)) {
                AppTextField(query, Modifier.fillMaxWidth(), placeholder = "Jump to a project, session or action", icon = Icons.Search, focusRequester = focus, onSubmit = { run() })
            }
            HorizontalRule()
            if (rows.isEmpty()) NoMatches() else Results(rows, matches.getOrNull(selected), list, { if (mouseActive) selected = matches.indexOf(it) }, { onDismiss(); it.run() })
            HorizontalRule()
            PaletteFooter()
        }
    }
}

@Composable
private fun NoMatches() {
    Box(Modifier.fillMaxWidth().padding(vertical = Spacing.xxl), contentAlignment = Alignment.Center) {
        Label("Nothing matches. Try a project name or an action.", color = colors.textTertiary)
    }
}

@Composable
private fun Results(rows: List<PaletteRow>, selected: PaletteItem?, list: androidx.compose.foundation.lazy.LazyListState, onHover: (PaletteItem) -> Unit, onChoose: (PaletteItem) -> Unit) {
    Box(Modifier.heightIn(max = listMaxHeight)) {
        LazyColumn(Modifier.fillMaxWidth(), list, contentPadding = androidx.compose.foundation.layout.PaddingValues(Spacing.sm)) {
            items(rows.size) { index ->
                when (val row = rows[index]) {
                    is PaletteRow.Heading -> Label(row.group.title, Modifier.padding(start = Spacing.sm, top = Spacing.sm, bottom = Spacing.xs), color = colors.textTertiary, style = Type.overline)
                    is PaletteRow.Entry -> ResultRow(row.item, row.item == selected, { onHover(row.item) }, { onChoose(row.item) })
                }
            }
        }
        ThinScrollbar(list)
    }
}

@Composable
private fun ResultRow(item: PaletteItem, selected: Boolean, onHover: () -> Unit, onChoose: () -> Unit) {
    Pressable(onChoose, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (selected) colors.accentSoft else androidx.compose.ui.graphics.Color.Transparent) { hovered ->
        LaunchedEffect(hovered) { if (hovered) onHover() }
        Row(Modifier.padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Box(Modifier.size(leadingBox), contentAlignment = Alignment.Center) {
                when {
                    item.avatarOf != null -> ProjectAvatar(item.avatarOf, size = 26.dp, cutout = colors.surfaceRaised)
                    item.status != null -> StatusDot(item.status)
                    item.icon != null -> Glyph(item.icon, if (selected) colors.accent else colors.textSecondary, size = 17.dp)
                }
            }
            Label(item.title, color = colors.textPrimary, style = Type.bodyMedium, modifier = Modifier.weight(1f, fill = false))
            if (item.subtitle.isNotEmpty()) Label(item.subtitle, Modifier.weight(1f), color = colors.textTertiary, style = Type.small, tooltipWhenTruncated = false)
            item.shortcut?.let { Shortcut(it) }
        }
    }
}

@Composable
private fun PaletteFooter() {
    Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.lg), verticalAlignment = Alignment.CenterVertically) {
        Hint("↑", "↓", text = "navigate")
        Hint(Icons.Enter, text = "open")
        Hint("Esc", text = "close")
    }
}

@Composable
private fun Hint(vararg keys: String, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 1.dp), verticalAlignment = Alignment.CenterVertically) {
        keys.forEach { Kbd(it) }
        Label(text, color = colors.textTertiary, style = Type.caption)
    }
}

@Composable
private fun Hint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 1.dp), verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, colors.textTertiary, size = 13.dp)
        Label(text, color = colors.textTertiary, style = Type.caption)
    }
}
