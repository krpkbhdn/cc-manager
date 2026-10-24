package com.github.krpkbhdn.ccmanager.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

private val MENU_WIDTH = 160.dp
private val MENU_SHAPE = RoundedCornerShape(6.dp)
private const val SURFACE_TINT = 0.08f
private const val BORDER_TINT = 0.18f
private const val DISABLED_ALPHA = 0.4f

/** Small themed menu with the clipboard actions of a terminal, shown at the pixel [offset] of the right click. */
@Composable
internal fun TerminalContextMenu(session: TerminalSession, offset: IntOffset, onDismiss: () -> Unit) {
    val theme = session.theme
    val surface = theme.foreground.copy(alpha = SURFACE_TINT).compositeOver(theme.background)
    val hasSelection = session.selection != null
    fun run(action: () -> Unit) {
        onDismiss()
        action()
        runCatching { session.focusRequester.requestFocus() }
    }
    Popup(offset = offset, onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Column(
            Modifier.clip(MENU_SHAPE).background(surface)
                .border(1.dp, theme.foreground.copy(alpha = BORDER_TINT), MENU_SHAPE).padding(vertical = 4.dp),
        ) {
            MenuItem("Copy", theme, hasSelection) { run(session::copySelection) }
            MenuItem("Paste", theme, true) { run(session::paste) }
            MenuItem("Select all", theme, true) { run(session::selectAll) }
        }
    }
}

@Composable
private fun MenuItem(label: String, theme: TerminalTheme, enabled: Boolean, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val highlight = if (hovered && enabled) theme.selection else Color.Transparent
    val color = theme.foreground.copy(alpha = if (enabled) 1f else DISABLED_ALPHA)
    BasicText(
        label,
        Modifier.width(MENU_WIDTH).background(highlight).hoverable(interaction)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 12.dp, vertical = 6.dp),
        style = TextStyle(color = color, fontSize = 13.sp),
    )
}
