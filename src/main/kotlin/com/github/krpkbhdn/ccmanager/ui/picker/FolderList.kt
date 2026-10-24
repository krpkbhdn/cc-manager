package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pill
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val rowHeight = 40.dp
private val folderIconSize = 20.dp
private const val SKELETON_ROWS = 6
private const val SKELETON_PULSE_MILLIS = 800
private const val SKELETON_LOW_ALPHA = 0.35f

/** Folder list of the current directory with loading, denied, missing and empty states. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FolderList(
    state: FolderPickerState,
    rows: List<FolderEntry>,
    listState: LazyListState,
    focus: FocusRequester,
    onKey: (KeyEvent) -> Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier.onKeyEvent(onKey).focusRequester(focus).focusable()) {
        when (val listing = state.listing) {
            null -> LoadingSkeleton()
            Listing.Denied -> EmptyState(AppIcon.Alert, "Access denied", "You do not have permission to open this folder. Pick another place from the left.")
            Listing.Missing -> EmptyState(AppIcon.Alert, "Folder not found", "This folder no longer exists. Go up or pick another place.")
            is Listing.Loaded -> if (rows.isEmpty()) NoFolders(state, listing) else FolderRows(state, rows, listState)
        }
    }
}

@Composable
private fun NoFolders(state: FolderPickerState, listing: Listing.Loaded) {
    val filtering = state.filter.text.isNotBlank()
    val hiddenCount = FolderRules.hiddenCount(listing.folders)
    when {
        filtering -> EmptyState(AppIcon.Folder, "No matching folders", "Nothing here matches \"${state.filter.text}\".")
        hiddenCount > 0 && !state.showHidden -> EmptyState(AppIcon.Folder, "Only hidden folders here", "${if (hiddenCount == 1) "1 hidden folder is" else "$hiddenCount hidden folders are"} collapsed. Use the eye button to show them.")
        else -> EmptyState(AppIcon.Folder, "No sub-folders", "Add this folder as a project, or create a new one inside it.")
    }
}

@Composable
private fun FolderRows(state: FolderPickerState, rows: List<FolderEntry>, listState: LazyListState) {
    LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(rows, key = { it.path.toString() }) { entry ->
            LaunchedEffect(entry.path) {
                if (state.badges[entry.path] == null) state.cacheBadges(entry.path, withContext(Dispatchers.IO) { FolderRules.badges(entry.path) })
            }
            FolderRow(entry, state.highlighted == entry.path, state.badges[entry.path].orEmpty(), { state.select(entry.path) }, { state.open(entry.path) })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(entry: FolderEntry, selected: Boolean, badges: Set<ProjectBadge>, onSelect: () -> Unit, onOpen: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    Row(
        Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(Radius.md))
            .background(if (selected) colors.surfaceSelected else if (hovered) colors.surfaceHover else colors.surface.copy(alpha = 0f))
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onDoubleClick = onOpen, onClick = onSelect)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        PickerGlyph(PickerIcon.FolderTinted, if (entry.hidden) colors.textTertiary else colors.accent, size = folderIconSize)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Label(entry.name, Modifier.weight(1f, fill = false), color = if (entry.hidden) colors.textSecondary else colors.textPrimary, weight = FontWeight.Medium)
            badges.sortedBy { it.ordinal }.forEach { Pill(it.label, badgeTint(it)) }
        }
        PickerGlyph(PickerIcon.ChevronRight, if (hovered || selected) colors.textTertiary else Color.Transparent)
    }
}

@Composable
private fun badgeTint(badge: ProjectBadge) = when (badge) {
    ProjectBadge.Git -> colors.success
    ProjectBadge.Claude -> colors.accent
    ProjectBadge.Build -> colors.textSecondary
}

@Composable
private fun LoadingSkeleton() {
    val pulse by rememberInfiniteTransition().animateFloat(
        1f, SKELETON_LOW_ALPHA, infiniteRepeatable(tween(SKELETON_PULSE_MILLIS), RepeatMode.Reverse),
    )
    Column(Modifier.fillMaxSize().padding(Spacing.md).alpha(pulse), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        repeat(SKELETON_ROWS) {
            Row(Modifier.height(rowHeight).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Box(Modifier.height(folderIconSize).fillMaxWidth(SKELETON_ICON_FRACTION).background(colors.surfaceHover, RoundedCornerShape(Radius.sm)))
                Box(Modifier.height(Spacing.md).fillMaxWidth(SKELETON_NAME_FRACTION - it * SKELETON_NAME_STEP).background(colors.surfaceHover, RoundedCornerShape(Radius.sm)))
            }
        }
    }
}

private const val SKELETON_ICON_FRACTION = 0.04f
private const val SKELETON_NAME_FRACTION = 0.5f
private const val SKELETON_NAME_STEP = 0.05f
