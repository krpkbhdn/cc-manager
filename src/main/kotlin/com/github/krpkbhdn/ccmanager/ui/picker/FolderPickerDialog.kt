package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.file.Path

private val dialogMaxWidth = 800.dp
private val dialogMaxHeight = 560.dp
private const val DIALOG_WIDTH_FRACTION = 0.94f
private const val DIALOG_HEIGHT_FRACTION = 0.92f
private const val ANIMATION_MILLIS = Motion.BASE
private const val CARD_START_SCALE = 0.96f

/** In-app modal folder browser used to add a project; replaces the native chooser. */
@Composable
fun FolderPickerDialog(initial: Path?, onDismiss: () -> Unit, onPicked: (Path) -> Unit) =
    FolderPicker(initial, RecentFolders.inDefaultDataDir(), onDismiss, onPicked)

/** The dialog with an injectable recents store, so tests and dev harnesses never touch the real data dir. */
@Composable
internal fun FolderPicker(initial: Path?, recents: RecentFolders, onDismiss: () -> Unit, onPicked: (Path) -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember { FolderPickerState(initial, scope, recents) }
    var open by remember { mutableStateOf(false) }
    val progress by animateFloatAsState(if (open) 1f else 0f, Motion.base())
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val rows = remember(state.listing, state.showHidden, state.filter.text) {
        (state.listing as? Listing.Loaded)?.let { FolderRules.visible(it.folders, state.showHidden, state.filter.text.toString()) }.orEmpty()
    }
    val check by produceState(PathCheck.Valid, state.target, state.listing) {
        value = withContext(Dispatchers.IO) { FolderRules.check(state.target) }
    }
    val listBlocked = state.target == state.current && state.listing.let { it == Listing.Denied || it == Listing.Missing }
    val canConfirm = check == PathCheck.Valid && !listBlocked
    fun closeThen(action: () -> Unit) {
        open = false
        scope.launch { delay(ANIMATION_MILLIS.toLong()); action() }
    }
    fun confirm() {
        if (!canConfirm) return
        val chosen = state.target
        state.recordChoice(chosen)
        closeThen { onPicked(chosen) }
    }
    LaunchedEffect(Unit) { open = true; listFocus.requestFocus() }
    val shape = RoundedCornerShape(Radius.xl)
    Box(
        Modifier.fillMaxSize().background(colors.scrim.copy(alpha = colors.scrim.alpha * progress))
            .pointerInput(Unit) { detectTapGestures { closeThen(onDismiss) } },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.widthIn(max = dialogMaxWidth).fillMaxWidth(DIALOG_WIDTH_FRACTION).heightIn(max = dialogMaxHeight).fillMaxHeight(DIALOG_HEIGHT_FRACTION)
                .graphicsLayer { alpha = progress; scaleX = CARD_START_SCALE + (1 - CARD_START_SCALE) * progress; scaleY = scaleX }
                .card(shape, colors.surfaceRaised, raised = true)
                .pointerInput(Unit) { detectTapGestures { } }
                .onKeyEvent { cardKey(it, state, ::confirm) { closeThen(onDismiss) } },
        ) {
            PickerHeader({ closeThen(onDismiss) })
            HorizontalRule()
            PickerToolbar(state, listFocus::requestFocus)
            HorizontalRule()
            PickerBody(state, rows, listState, listFocus, ::confirm, Modifier.weight(1f))
            PickerFooter(state, if (listBlocked) PathCheck.Unreadable else check, { closeThen(onDismiss) }, ::confirm)
        }
    }
}

@Composable
private fun PickerBody(state: FolderPickerState, rows: List<FolderEntry>, listState: LazyListState, focus: FocusRequester, onConfirm: () -> Unit, modifier: Modifier) {
    val scope = rememberCoroutineScope()
    LaunchedEffect(state.listing) {
        listState.scrollToItem(0)
        rows.indexOfFirst { it.path == state.highlighted }.takeIf { it >= 0 }?.let { listState.scrollToVisible(it) }
    }
    Row(modifier.fillMaxWidth()) {
        PlacesRail(state, focus::requestFocus)
        VerticalRule()
        Column(Modifier.weight(1f).fillMaxHeight()) {
            FilterRow(state, rows.size, focus::requestFocus)
            if (state.creatingFolder) NewFolderRow(state)
            FolderList(
                state, rows, listState, focus,
                onKey = { state.handleListKey(it, rows, { index -> scope.launch { listState.scrollToVisible(index) } }, onConfirm) },
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
        }
    }
}

private suspend fun LazyListState.scrollToVisible(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || index <= visible.first().index || index >= visible.last().index) animateScrollToItem((index - 1).coerceAtLeast(0))
}

private fun cardKey(event: KeyEvent, state: FolderPickerState, onConfirm: () -> Unit, onClose: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.key == Key.Escape -> true.also { if (!state.escape()) onClose() }
        event.isCtrlPressed && event.key == Key.L -> true.also { state.startEditingPath() }
        event.isCtrlPressed && event.key == Key.Enter -> true.also { onConfirm() }
        else -> false
    }
}
