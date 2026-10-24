package com.github.krpkbhdn.ccmanager.ui.picker

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Motion
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val rowHeight = 40.dp
private const val SKELETON_ROWS = 6
private const val SKELETON_ICON_FRACTION = 0.04f
private const val SKELETON_NAME_FRACTION = 0.5f
private const val SKELETON_NAME_STEP = 0.06f

/** Folder list of the current directory with loading, denied, missing and empty states. */
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
            Listing.Denied -> EmptyState(Icons.Alert, "Access denied", "You do not have permission to open this folder. Pick another place from the left.", tint = colors.danger)
            Listing.Missing -> EmptyState(Icons.Alert, "Folder not found", "This folder no longer exists. Go up or pick another place.", tint = colors.warning)
            is Listing.Loaded -> if (rows.isEmpty()) NoFolders(state, listing) else FolderRows(state, rows, listState)
        }
    }
}

@Composable
private fun NoFolders(state: FolderPickerState, listing: Listing.Loaded) {
    val filtering = state.filter.text.isNotBlank()
    val hiddenCount = FolderRules.hiddenCount(listing.folders)
    when {
        filtering -> EmptyState(Icons.Search, "No matching folders", "Nothing here matches \"${state.filter.text}\".")
        hiddenCount > 0 && !state.showHidden ->
            EmptyState(Icons.Eye, "Only hidden folders here", "${if (hiddenCount == 1) "1 hidden folder is" else "$hiddenCount hidden folders are"} collapsed. Use the eye button to show them.")
        else -> EmptyState(Icons.FolderOpen, "No sub-folders", "Add this folder as a project, or create a new one inside it.")
    }
}

@Composable
private fun FolderRows(state: FolderPickerState, rows: List<FolderEntry>, listState: LazyListState) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), listState, contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            items(rows, key = { it.path.toString() }) { entry ->
                LaunchedEffect(entry.path) {
                    if (state.badges[entry.path] == null) state.cacheBadges(entry.path, withContext(Dispatchers.IO) { FolderRules.badges(entry.path) })
                }
                FolderRow(entry, state.highlighted == entry.path, state.badges[entry.path].orEmpty(), { state.select(entry.path) }, { state.open(entry.path) })
            }
        }
        ThinScrollbar(listState)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderRow(entry: FolderEntry, selected: Boolean, badges: Set<ProjectBadge>, onSelect: () -> Unit, onOpen: () -> Unit) {
    val source = remember { MutableInteractionSource() }
    val hovered by source.collectIsHoveredAsState()
    val surface by animateColorAsState(
        when {
            selected -> colors.accentSoft
            hovered -> colors.hoverOverlay
            else -> Color.Transparent
        },
        Motion.fast(), label = "folderRowSurface",
    )
    Row(
        Modifier.fillMaxWidth().height(rowHeight).clip(RoundedCornerShape(Radius.md)).background(surface)
            .hoverable(source).pointerHoverIcon(PointerIcon.Hand)
            .combinedClickable(interactionSource = source, indication = null, onDoubleClick = onOpen, onClick = onSelect)
            .padding(horizontal = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Glyph(if (selected) Icons.FolderOpen else Icons.Folder, if (selected) colors.accent else if (entry.hidden) colors.textTertiary else colors.textSecondary, size = IconSize.xlarge)
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(entry.name, Modifier.weight(1f, fill = false), color = if (entry.hidden) colors.textSecondary else colors.textPrimary, style = Type.bodyMedium)
            badges.sortedBy { it.ordinal }.forEach { badge -> Chip(badge.label, badgeTint(badge), leading = { Glyph(badgeIcon(badge), badgeTint(badge), size = IconSize.small) }) }
        }
        Glyph(Icons.ChevronRight, if (hovered || selected) colors.textTertiary else Color.Transparent, size = IconSize.large)
    }
}

@Composable
private fun badgeTint(badge: ProjectBadge) = when (badge) {
    ProjectBadge.Git -> colors.success
    ProjectBadge.Claude -> colors.accent
    ProjectBadge.Build -> colors.textSecondary
}

private fun badgeIcon(badge: ProjectBadge): ImageVector = when (badge) {
    ProjectBadge.Git -> Icons.Branch
    ProjectBadge.Claude -> Icons.Sparkles
    ProjectBadge.Build -> Icons.Cpu
}

@Composable
private fun LoadingSkeleton() {
    Column(Modifier.fillMaxSize().padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        repeat(SKELETON_ROWS) {
            Row(Modifier.height(rowHeight).padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                SkeletonBlock(Modifier.height(IconSize.xlarge).fillMaxWidth(SKELETON_ICON_FRACTION))
                SkeletonBlock(Modifier.height(Spacing.md).fillMaxWidth(SKELETON_NAME_FRACTION - it * SKELETON_NAME_STEP))
            }
        }
    }
}
