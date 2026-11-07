package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.MenuColumn
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import kotlinx.coroutines.delay

private val flyoutMinWidth = 200.dp
private const val FLYOUT_CLOSE_DELAY_MILLIS = 150L

/** True while the pointer is over the rail icon or the flyout itself; either keeps the flyout open. */
internal fun flyoutWanted(anchorHovered: Boolean, flyoutHovered: Boolean): Boolean = anchorHovered || flyoutHovered

/** Puts the flyout right of the anchor, top-aligned, and shifts it up when it would leave the window. */
private object BesideAnchor : PopupPositionProvider {
    override fun calculatePosition(anchorBounds: IntRect, windowSize: IntSize, layoutDirection: LayoutDirection, popupContentSize: IntSize): IntOffset {
        val top = anchorBounds.top.coerceAtMost(windowSize.height - popupContentSize.height).coerceAtLeast(0)
        return IntOffset(anchorBounds.right, top)
    }
}

/** Rail icon that opens a menu of [entries] on hover and closes it when the pointer leaves both or on an outside click; the menu takes no focus so the terminal keeps it. */
@Composable
fun HoverFlyout(entries: List<MenuEntry>, modifier: Modifier = Modifier, anchor: @Composable () -> Unit) {
    val anchorSource = remember { MutableInteractionSource() }
    val flyoutSource = remember { MutableInteractionSource() }
    val wanted = flyoutWanted(anchorSource.collectIsHoveredAsState().value, flyoutSource.collectIsHoveredAsState().value)
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(wanted) {
        if (wanted) {
            open = true
        } else {
            delay(FLYOUT_CLOSE_DELAY_MILLIS)
            open = false
        }
    }
    Box(modifier.hoverable(anchorSource)) {
        anchor()
        if (open) {
            AnchoredPopup({ open = false }, modifier = Modifier.widthIn(min = flyoutMinWidth).width(IntrinsicSize.Max).hoverable(flyoutSource), focusable = false, positionProvider = BesideAnchor) {
                MenuColumn(entries) { open = false }
            }
        }
    }
}

/** Wraps [content] in a [HoverFlyout] on the rail; the expanded sidebar shows it as is. */
@Composable
fun RailFlyoutIf(rail: Boolean, entries: List<MenuEntry>, content: @Composable () -> Unit) {
    if (rail) HoverFlyout(entries, anchor = content) else content()
}
