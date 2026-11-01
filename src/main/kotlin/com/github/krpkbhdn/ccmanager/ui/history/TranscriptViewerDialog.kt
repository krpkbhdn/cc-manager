package com.github.krpkbhdn.ccmanager.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService.Window
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.screens.LogEntry
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 760.dp
private val bodyMinHeight = 96.dp
private val bodyMaxHeight = 520.dp

/** Read-only part of a stored transcript around a search hit; [window] is null while it loads, the hit's entry is highlighted. */
@Composable
fun TranscriptViewerDialog(title: String, window: Window?, onClose: () -> Unit) {
    ModalHost(onClose, width = dialogWidth) {
        Column(Modifier.padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Label(title, style = Type.subtitle, tooltipWhenTruncated = true)
            Box(Modifier.fillMaxWidth().heightIn(min = bodyMinHeight, max = bodyMaxHeight).background(colors.terminal, RoundedCornerShape(Radius.md))) { ViewerBody(window) }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AppButton("Close", onClose) }
        }
    }
}

@Composable
private fun ViewerBody(window: Window?) {
    when {
        window == null -> Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            repeat(3) { SkeletonBlock(Modifier.fillMaxWidth().height(40.dp)) }
        }
        window.entries().isEmpty() -> Label("The transcript is no longer available or could not be read.", Modifier.padding(Spacing.lg), color = colors.textTertiary, maxLines = 3)
        else -> EntryWindow(window)
    }
}

@Composable
private fun EntryWindow(window: Window) {
    val list = rememberLazyListState()
    LaunchedEffect(window) { if (window.index() >= 0) list.scrollToItem(window.index()) }
    Box {
        LazyColumn(Modifier.fillMaxWidth(), list, verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            itemsIndexed(window.entries()) { index, entry ->
                val highlight = if (index == window.index()) Modifier.background(colors.accent.copy(alpha = Alpha.SOFT), RoundedCornerShape(Radius.sm)) else Modifier
                LogEntry(entry, Modifier.fillMaxWidth().then(highlight).padding(Spacing.sm))
            }
        }
        ThinScrollbar(list)
    }
}
