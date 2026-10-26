package com.github.krpkbhdn.ccmanager.ui.git.branch

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitBranch
import com.github.krpkbhdn.ccmanager.ui.components.ContextMenuArea
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.checkout
import com.github.krpkbhdn.ccmanager.ui.git.model.commitAge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.time.Instant

private val rowHeight = 44.dp

/** Local branches, then remote ones grouped by remote; a click checks the branch out, the menu offers everything else. */
@Composable
fun BranchList(git: GitState, query: String, onPicked: () -> Unit, modifier: Modifier = Modifier) {
    val items = remember(git.branches, query) { branchListItems(branchGroups(git.branches, query)) }
    if (items.isEmpty()) {
        EmptyState(Icons.Branch, "No branches", if (query.isBlank()) "This repository has no branches yet." else "Nothing matches \"$query\".", modifier)
        return
    }
    val state = rememberLazyListState()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), state, contentPadding = PaddingValues(Spacing.xs)) {
            items(items, key = { if (it is BranchListItem.Entry) "b-${it.branch.remote()}-${it.branch.name()}" else "h-${(it as BranchListItem.Heading).title}" }) { item ->
                when (item) {
                    is BranchListItem.Heading -> Heading(item)
                    is BranchListItem.Entry -> BranchRow(git, item.branch, onPicked)
                }
            }
        }
        ThinScrollbar(state)
    }
}

@Composable
private fun Heading(item: BranchListItem.Heading) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.md, top = Spacing.md, bottom = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(item.title.uppercase(), color = colors.textTertiary, style = Type.overline)
        Label(item.count.toString(), color = colors.textTertiary, style = Type.numericCaption)
    }
}

@Composable
private fun BranchRow(git: GitState, branch: GitBranch, onPicked: () -> Unit) {
    var menuOpen by remember { mutableStateOf(false) }
    ContextMenuArea({ branchMenu(git, branch) }) {
        Pressable(
            { if (!branch.current()) { git.checkout(branch.name()); onPicked() } },
            Modifier.fillMaxWidth(),
            RoundedCornerShape(Radius.sm),
            base = if (branch.current()) colors.accentSoft else Color.Transparent,
        ) { hovered ->
            Row(Modifier.height(rowHeight).padding(horizontal = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Box(Modifier.width(20.dp), contentAlignment = Alignment.Center) {
                    if (branch.current()) Glyph(Icons.Check, colors.accent, size = IconSize.large) else Glyph(if (branch.remote()) Icons.Globe else Icons.Branch, colors.textTertiary, size = IconSize.medium)
                }
                Column(Modifier.weight(1f)) {
                    Label(branch.name(), color = colors.textPrimary, style = if (branch.current()) Type.monoMedium else Type.monoBody.copy(fontSize = Type.monoMedium.fontSize), tooltipWhenTruncated = true)
                    Label(subtitle(branch), color = colors.textTertiary, style = Type.caption)
                }
                Badge(Icons.ArrowUp, branch.ahead(), colors.success)
                Badge(Icons.ArrowDown, branch.behind(), colors.warning)
                if (hovered || menuOpen) Box { IconButton(Icons.More, { menuOpen = true }, tooltip = "Branch actions", size = 24.dp); if (menuOpen) PopupMenu(branchMenu(git, branch), { menuOpen = false }, alignEnd = true) }
            }
        }
    }
}

@Composable
private fun Badge(icon: androidx.compose.ui.graphics.vector.ImageVector, count: Int, tint: Color) {
    if (count == 0) return
    Row(verticalAlignment = Alignment.CenterVertically) {
        Glyph(icon, tint, size = IconSize.small)
        Label(count.toString(), color = tint, style = Type.numericCaption)
    }
}

private fun subtitle(branch: GitBranch): String {
    val age = branch.date()?.let { commitAge(it, Instant.now()) }
    return listOfNotNull(branch.subject()?.takeIf { it.isNotBlank() }, age).joinToString("  ·  ")
}
