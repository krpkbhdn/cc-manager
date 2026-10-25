package com.github.krpkbhdn.ccmanager.ui.extensions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val paneWidth = 380.dp
private val skeletonRowHeight = 62.dp
private const val SKELETON_ROWS = 5

/** Top strip of an extensions page: tabs, filters and actions in [content], closed by a rule. */
@Composable
fun ExtensionToolbar(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md), content = content)
        HorizontalRule()
    }
}

/** A list that gives way to a [detail] pane on its right while something is selected. */
@Composable
fun ListWithDetail(detail: (@Composable () -> Unit)?, modifier: Modifier = Modifier, list: @Composable () -> Unit) {
    Row(modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) { list() }
        if (detail != null) {
            VerticalRule()
            detail()
        }
    }
}

/** Detail side pane: identity and close button, [badges] and [actions] under it, then the scrolling [content]. */
@Composable
fun DetailPaneFrame(
    avatarName: String,
    title: String,
    subtitle: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    Column(modifier.width(paneWidth).fillMaxHeight().padding(top = Spacing.lg)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ExtensionAvatar(avatarName, size = 44.dp)
            Column(Modifier.weight(1f)) {
                Label(title, style = Type.subtitle, tooltipWhenTruncated = true)
                Label(subtitle, color = colors.textTertiary, style = Type.small, tooltipWhenTruncated = true)
            }
            IconButton(Icons.Close, onClose, tooltip = "Close")
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            badges()
            Box(Modifier.weight(1f))
            actions()
        }
        HorizontalRule()
        Box(Modifier.weight(1f)) { content() }
    }
}

@Composable
fun ListSkeleton(modifier: Modifier = Modifier) {
    Column(modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(SKELETON_ROWS) { SkeletonBlock(Modifier.fillMaxWidth().height(skeletonRowHeight), RoundedCornerShape(14.dp)) }
    }
}

/** A dialog field with its [label] above it and an optional [hint] below. */
@Composable
fun FormField(label: String, modifier: Modifier = Modifier, hint: String? = null, error: String? = null, content: @Composable () -> Unit) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(label, style = Type.smallMedium)
        content()
        if (error != null) Label(error, color = colors.danger, style = Type.caption, maxLines = 2)
        else if (hint != null) Label(hint, color = colors.textTertiary, style = Type.caption, maxLines = 2)
    }
}

/** Selectable monospace text on the terminal background, for raw CLI output and file contents. */
@Composable
fun MonoBlock(text: String, modifier: Modifier = Modifier) {
    SelectionContainer {
        Box(modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.md)).background(colors.terminal).padding(Spacing.md)) {
            Label(text, color = colors.textSecondary, style = Type.monoSmall, maxLines = Int.MAX_VALUE)
        }
    }
}

/** A sidebar screen: [title] and [subtitle] above the page, which gets the remaining height. */
@Composable
fun ExtensionScreen(title: String, subtitle: String, modifier: Modifier = Modifier, page: @Composable (Modifier) -> Unit) {
    Column(modifier.fillMaxSize()) {
        Column(Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg)) {
            Label(title, style = Type.title)
            Label(subtitle, color = colors.textSecondary, style = Type.small)
        }
        page(Modifier.weight(1f))
    }
}

/** Scrolling list of row cards with the page padding and a thin scrollbar. */
@Composable
fun ExtensionList(modifier: Modifier = Modifier, content: LazyListScope.() -> Unit) {
    val list = rememberLazyListState()
    Box(modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.md, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm), content = content)
        ThinScrollbar(list)
    }
}

/** A titled group of rows; nothing is added when [entries] is empty. */
fun <T> LazyListScope.listSection(id: String, title: String, entries: List<T>, key: (T) -> String, row: @Composable (T) -> Unit) {
    if (entries.isEmpty()) return
    item(key = id) { SectionHeader(title, entries.size) }
    items(entries, key = key) { row(it) }
}
