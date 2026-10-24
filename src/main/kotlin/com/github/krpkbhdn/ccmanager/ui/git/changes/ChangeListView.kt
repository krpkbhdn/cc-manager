package com.github.krpkbhdn.ccmanager.ui.git.changes

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.git.GitMisc.ConflictSide
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeGroup
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeItem
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeRef
import com.github.krpkbhdn.ccmanager.ui.git.model.ChangeSections
import com.github.krpkbhdn.ccmanager.ui.git.model.changeItems
import com.github.krpkbhdn.ccmanager.ui.git.model.pathsUnder
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing

/** Everything a change list can ask for; paths are repository-relative. */
class ChangeListActions(
    val onSelect: (ChangeRef) -> Unit,
    val onStage: (List<String>) -> Unit,
    val onUnstage: (List<String>) -> Unit,
    val onDiscard: (List<String>) -> Unit,
    val onShowHistory: (String) -> Unit,
    val onOpenFolder: (String) -> Unit,
    val onOpenIde: (String) -> Unit,
    val onResolve: (String, ConflictSide) -> Unit,
    val onMarkResolved: (String) -> Unit,
)

/** Conflicts, staged and unstaged files as one scrolling list, as a flat list or a folder tree. */
@Composable
fun ChangeListView(
    sections: ChangeSections,
    selected: ChangeRef?,
    flat: Boolean,
    actions: ChangeListActions,
    modifier: Modifier = Modifier,
) {
    var collapsedGroups by remember { mutableStateOf(emptySet<ChangeGroup>()) }
    var collapsedFolders by remember { mutableStateOf(emptySet<String>()) }
    val items = remember(sections, flat, collapsedGroups, collapsedFolders) { changeItems(sections, flat, collapsedGroups, collapsedFolders) }
    val state = rememberLazyListState()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(horizontal = Spacing.sm, vertical = Spacing.xs)) {
            items(items, key = ::itemKey) { item ->
                when (item) {
                    is ChangeItem.Header -> GroupHeader(item, { collapsedGroups = collapsedGroups.toggle(item.group) }, groupAction(item.group, sections, actions))
                    is ChangeItem.Folder -> FolderRow(item, { collapsedFolders = collapsedFolders.toggle(item.key) }, folderToggle(item, sections, actions))
                    is ChangeItem.File -> FileRow(item, selected, actions)
                }
            }
        }
        ThinScrollbar(state)
    }
}

private fun itemKey(item: ChangeItem): String = when (item) {
    is ChangeItem.Header -> "header-${item.group}"
    is ChangeItem.Folder -> "folder-${item.key}"
    is ChangeItem.File -> "file-${item.group}-${item.row.change.path()}"
}

private fun <T> Set<T>.toggle(value: T): Set<T> = if (value in this) this - value else this + value

private fun groupAction(group: ChangeGroup, sections: ChangeSections, actions: ChangeListActions): GroupAction? = when (group) {
    ChangeGroup.STAGED -> GroupAction("Unstage all") { actions.onUnstage(sections.staged.map { it.path() }) }
    ChangeGroup.UNSTAGED -> GroupAction("Stage all") { actions.onStage(sections.unstaged.map { it.path() }) }
    ChangeGroup.CONFLICTS -> null
}

private fun folderToggle(item: ChangeItem.Folder, sections: ChangeSections, actions: ChangeListActions): (Boolean) -> Unit = { stage ->
    val changes = if (item.group == ChangeGroup.STAGED) sections.staged else sections.unstaged
    val paths = pathsUnder(changes, item.row.path)
    if (stage) actions.onStage(paths) else actions.onUnstage(paths)
}
