package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset

/** Opens [entries] as a menu at the cursor on a right click inside [content]; [entries] is read when the menu opens. */
@Composable
fun ContextMenuArea(entries: () -> List<MenuEntry>, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    var openAt by remember { mutableStateOf<IntOffset?>(null) }
    Box(
        modifier.pointerInput(Unit) {
            awaitPointerEventScope {
                while (true) {
                    val event = awaitPointerEvent()
                    if (event.type == PointerEventType.Press && event.buttons.isSecondaryPressed) {
                        val position = event.changes.first().position
                        openAt = IntOffset(position.x.toInt(), position.y.toInt())
                    }
                }
            }
        },
    ) {
        content()
        openAt?.let { PopupMenu(entries(), onDismiss = { openAt = null }, at = it) }
    }
}
