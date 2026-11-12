package com.github.krpkbhdn.ccmanager.ui.web

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.delay

private const val CLOSE_DELAY_MILLIS = 250L
private val pinGap = 14.dp
private val popoverMaxWidth = 280.dp
private val popoverReserveHeight = 140.dp
private val actions = WebPinActions()

/** The state the popover's Edit, Go to and Delete act on; provided by the screen that hosts the overlay. */
val LocalWebState = staticCompositionLocalOf<WebState?> { null }

/**
 * Card near the hovered pin with the item's number, status, comment and actions; stays while the pointer is over it.
 * The pin rect is in CSS px; [viewport] puts it in canvas dp.
 */
@Composable
fun WebItemPopover(view: WebView, viewport: PageViewport) {
    val state = LocalWebState.current
    LaunchedEffect(view.selectedItemId) { actions.emphasiseSelection(view) }
    val source = remember { MutableInteractionSource() }
    val overCard by source.collectIsHoveredAsState()
    var shown by remember { mutableStateOf<ItemPopoverState?>(null) }
    val hovered = view.itemPopover
    LaunchedEffect(hovered, overCard) {
        when {
            hovered != null -> shown = hovered
            !overCard -> { delay(CLOSE_DELAY_MILLIS); shown = null }
        }
    }
    val current = shown ?: return
    val item = view.visibleItems.firstOrNull { it.id().toString() == current.itemId } ?: return
    val at = viewport.toCanvas(Offset((current.rect?.x ?: 0.0).toFloat(), (current.rect?.y ?: 0.0).toFloat()))
    val shape = RoundedCornerShape(Radius.md)
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val x = at.x.dp.coerceIn(0.dp, (maxWidth - popoverMaxWidth).coerceAtLeast(0.dp))
        val y = (at.y.dp + pinGap).coerceIn(0.dp, (maxHeight - popoverReserveHeight).coerceAtLeast(0.dp))
        Column(
            Modifier.offset(x, y).widthIn(max = popoverMaxWidth)
                .background(colors.surface, shape).border(1.dp, colors.border, shape).hoverable(source).padding(Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            Label("#${item.n()} ${item.status().name.lowercase().replaceFirstChar(Char::titlecase)}", style = Type.bodyMedium)
            Label(item.comment().ifBlank { "No comment" }, color = if (item.comment().isBlank()) colors.textTertiary else colors.textPrimary, maxLines = 6)
            if (state != null) Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                IconButton(Icons.Edit, { state.editItem(view, item) }, tooltip = "Edit")
                IconButton(Icons.Search, { state.selectItem(view, item) }, tooltip = "Go to")
                IconButton(Icons.Trash, { actions.delete(view, item, state::delete) }, tooltip = "Delete", tint = colors.danger)
            }
        }
    }
}
