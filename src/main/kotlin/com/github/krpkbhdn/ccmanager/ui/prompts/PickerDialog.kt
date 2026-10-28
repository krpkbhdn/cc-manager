package com.github.krpkbhdn.ccmanager.ui.prompts

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.DialogBody
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val dialogWidth = 560.dp
private const val KEYS_HINT = "Up and Down to move, Enter to choose, Esc to close."

/** Modal list of cards: Up/Down move, Enter or a click picks one through [onChoose], Esc or a click outside closes. [card] draws one item. */
@Composable
internal fun <T> PickerDialog(
    title: String,
    icon: ImageVector,
    items: List<T>,
    emptyHint: String,
    onChoose: (T) -> Unit,
    onDismiss: () -> Unit,
    card: @Composable (item: T, selected: Boolean, onHover: () -> Unit) -> Unit,
) {
    var selected by remember { mutableIntStateOf(0) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ModalHost(onDismiss, width = dialogWidth, takeFocus = false) {
        DialogBody(title, icon, colors.accent) {
            Column(
                Modifier.fillMaxWidth().focusRequester(focus).focusable().onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.DirectionDown -> { selected = movedSelection(selected, 1, items.size); true }
                        Key.DirectionUp -> { selected = movedSelection(selected, -1, items.size); true }
                        Key.Enter, Key.NumPadEnter -> { items.getOrNull(selected)?.let(onChoose); true }
                        else -> false
                    }
                },
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (items.isEmpty()) Label(emptyHint, color = colors.textTertiary, maxLines = 2)
                items.forEachIndexed { index, item -> card(item, index == selected) { selected = index } }
                if (items.isNotEmpty()) Label(KEYS_HINT, color = colors.textTertiary, style = Type.caption)
            }
        }
    }
}

/** One selectable card of a [PickerDialog]; [onHover] moves the selection to it. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun PickerCard(selected: Boolean, onHover: () -> Unit, onChoose: () -> Unit, content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    val inView = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) inView.bringIntoView() }
    Pressable(
        onChoose,
        Modifier.fillMaxWidth().bringIntoViewRequester(inView).border(1.dp, if (selected) colors.accent else colors.border, shape),
        shape,
        base = if (selected) colors.surfaceHover else colors.surface,
        hover = colors.surfaceHover,
    ) { hovered ->
        LaunchedEffect(hovered) { if (hovered) onHover() }
        Column(Modifier.fillMaxWidth().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) { content() }
    }
}
