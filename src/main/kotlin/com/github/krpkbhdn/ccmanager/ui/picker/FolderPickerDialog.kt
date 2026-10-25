package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindow
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberDialogState
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.drawAppIcon
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Dimension
import java.awt.GraphicsEnvironment
import java.nio.file.Path

private const val DIALOG_TITLE = "Add project"
private const val DIALOG_ICON_SIZE = 256
private const val BOUNDS_SAVE_DELAY_MILLIS = 400L
private val compactRailBelow = 700.dp

/** Movable, resizable, modal folder browser used to add a project; it is a native dialog window, so it can leave the main window and use any monitor. */
@Composable
fun FolderPickerDialog(initial: Path?, dataDir: Path, onDismiss: () -> Unit, onPicked: (Path) -> Unit) =
    FolderPicker(initial, RecentFolders.inDataDir(dataDir), PickerWindowStore.inDataDir(dataDir), onDismiss, onPicked)

/** The dialog with injectable stores, so tests and dev harnesses never touch the real data dir. */
@OptIn(FlowPreview::class)
@Composable
internal fun FolderPicker(initial: Path?, recents: RecentFolders, windowStore: PickerWindowStore, onDismiss: () -> Unit, onPicked: (Path) -> Unit) {
    val scope = rememberCoroutineScope()
    val state = remember { FolderPickerState(initial, scope, recents) }
    val listFocus = remember { FocusRequester() }
    val listState = rememberLazyListState()
    val icon = remember { BitmapPainter(drawAppIcon(DIALOG_ICON_SIZE).toComposeImageBitmap()) }
    val dialogState = rememberDialogState(position = WindowPosition.PlatformDefault, size = DpSize.Unspecified)
    remember {
        val bounds = windowStore.load().let { it ?: PickerBounds(null, null, PickerWindowStore.DEFAULT_WIDTH, PickerWindowStore.DEFAULT_HEIGHT) }
            .let { PickerWindowStore.clamp(it, screenBounds()) }
        dialogState.size = DpSize(bounds.width.dp, bounds.height.dp)
        dialogState.position = if (bounds.x != null && bounds.y != null) WindowPosition(bounds.x.dp, bounds.y.dp) else WindowPosition(Alignment.Center)
    }
    LaunchedEffect(dialogState) {
        snapshotFlow { dialogState.size to dialogState.position }.debounce(BOUNDS_SAVE_DELAY_MILLIS).collect { (size, position) ->
            if (position.isSpecified) windowStore.save(PickerBounds(position.x.value.toInt(), position.y.value.toInt(), size.width.value.toInt(), size.height.value.toInt()))
        }
    }
    val rows = remember(state.listing, state.showHidden, state.filter.text) {
        (state.listing as? Listing.Loaded)?.let { FolderRules.visible(it.folders, state.showHidden, state.filter.text.toString()) }.orEmpty()
    }
    val check by produceState(PathCheck.Valid, state.target, state.listing) {
        value = withContext(Dispatchers.IO) { FolderRules.check(state.target) }
    }
    val listBlocked = state.target == state.current && state.listing.let { it == Listing.Denied || it == Listing.Missing }
    val canConfirm = check == PathCheck.Valid && !listBlocked
    fun confirm() {
        if (!canConfirm) return
        val chosen = state.target
        state.recordChoice(chosen)
        onPicked(chosen)
    }
    DialogWindow(onCloseRequest = onDismiss, state = dialogState, title = DIALOG_TITLE, icon = icon, resizable = true) {
        window.minimumSize = Dimension(PickerWindowStore.MIN_WIDTH, PickerWindowStore.MIN_HEIGHT)
        LaunchedEffect(Unit) { withFrameNanos { }; listFocus.requestFocus() }
        Column(Modifier.fillMaxSize().background(colors.surfaceRaised).onKeyEvent { dialogKey(it, state, ::confirm, onDismiss) }) {
            PickerHeader()
            HorizontalRule()
            PickerToolbar(state, listFocus::requestFocus)
            HorizontalRule()
            PickerBody(state, rows, listState, listFocus, ::confirm, Modifier.weight(1f))
            PickerFooter(state, if (listBlocked) PathCheck.Unreadable else check, onDismiss, ::confirm)
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
    BoxWithConstraints(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxSize()) {
            PlacesRail(state, focus::requestFocus, compact = this@BoxWithConstraints.maxWidth < compactRailBelow)
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
}

private fun screenBounds() = GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.map { it.defaultConfiguration.bounds }

private suspend fun LazyListState.scrollToVisible(index: Int) {
    val visible = layoutInfo.visibleItemsInfo
    if (visible.isEmpty() || index <= visible.first().index || index >= visible.last().index) animateScrollToItem((index - 1).coerceAtLeast(0))
}

private fun dialogKey(event: KeyEvent, state: FolderPickerState, onConfirm: () -> Unit, onClose: () -> Unit): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    return when {
        event.key == Key.Escape -> true.also { if (!state.escape()) onClose() }
        event.isCtrlPressed && event.key == Key.L -> true.also { state.startEditingPath() }
        event.isCtrlPressed && event.key == Key.Enter -> true.also { onConfirm() }
        else -> false
    }
}
