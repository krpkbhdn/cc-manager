package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import kotlinx.coroutines.launch

private const val WHEEL_PIXELS_PER_NOTCH = 60f

/** Lists every closable tab while the strip overflows, so a tab hidden off the edge is one click away. */
@Composable
fun TabOverflowButton(tabs: List<TabSpec>, selected: ProjectTab, onSelect: (ProjectTab) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(start = Spacing.xs)) {
        IconButton(Icons.ChevronDown, { expanded = true }, tooltip = "All tabs")
        if (expanded) {
            val entries = tabs.map { MenuEntry(it.label, it.icon, it.status, if (it.tab == selected) "current" else null) { onSelect(it.tab) } }
            PopupMenu(entries, onDismiss = { expanded = false }, alignEnd = true)
        }
    }
}

/** Turns the vertical mouse wheel into horizontal scrolling; horizontal deltas stay with the scroll container. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun Modifier.wheelScrollsHorizontally(scroll: ScrollState): Modifier {
    val scope = rememberCoroutineScope()
    return onPointerEvent(PointerEventType.Scroll) { event ->
        val delta = event.changes.first().scrollDelta
        if (delta.x == 0f && delta.y != 0f) scope.launch { scroll.scrollBy(delta.y * WHEEL_PIXELS_PER_NOTCH) }
    }
}
