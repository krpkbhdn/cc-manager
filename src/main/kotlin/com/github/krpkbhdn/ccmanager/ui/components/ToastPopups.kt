package com.github.krpkbhdn.ccmanager.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.github.krpkbhdn.ccmanager.ui.state.ScreenArea
import com.github.krpkbhdn.ccmanager.ui.state.ToastItem
import com.github.krpkbhdn.ccmanager.ui.state.popupOrigin
import com.github.krpkbhdn.ccmanager.ui.theme.AppTheme
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

private val popupWidth = toastWidth + Spacing.lg * 2
private val popupSlotHeight = 170.dp

/**
 * Toasts as small always-on-top windows in the screen corner, for when the main window is hidden or unfocused.
 * A dialog (no taskbar button) that never takes focus, so it does not steal typing from another app.
 */
@Composable
fun ToastPopups(toasts: List<ToastItem>, area: ScreenArea, dark: Boolean, onOpen: (ToastItem) -> Unit, onDismiss: (Long) -> Unit) {
    toasts.forEachIndexed { index, toast ->
        key(toast.id) {
            val (x, y) = popupOrigin(area, index, popupWidth.value.toInt(), popupSlotHeight.value.toInt(), 0)
            val state = rememberDialogState(position = WindowPosition(x.dp, y.dp), size = DpSize(popupWidth, popupSlotHeight))
            SideEffect { state.position = WindowPosition(x.dp, y.dp) }
            DialogWindow(
                onCloseRequest = { onDismiss(toast.id) },
                state = state,
                undecorated = true,
                transparent = true,
                resizable = false,
                focusable = false,
                alwaysOnTop = true,
            ) {
                AppTheme(dark) {
                    Box(Modifier.fillMaxSize().padding(Spacing.lg), contentAlignment = Alignment.BottomEnd) {
                        Toast(toast, { onOpen(toast) }, { onDismiss(toast.id) })
                    }
                }
            }
        }
    }
}
