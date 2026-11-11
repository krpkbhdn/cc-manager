package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private fun CatalogTab.icon(): ImageVector = when (this) {
    CatalogTab.TOOLS -> Icons.Plug
    CatalogTab.CONFIG -> Icons.Settings
}

/** The left pane: the two areas, each with how many of its items are new or updatable. */
@Composable
fun AuthorAreaPane(view: CatalogView, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().background(colors.sidebar).padding(Spacing.sm + 2.dp), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        CatalogTab.entries.forEach { tab ->
            AreaRow(tab, view.tab == tab && !view.searching, CatalogSelection.available(view.entries, tab)) { view.tab = tab }
        }
    }
}

@Composable
private fun AreaRow(tab: CatalogTab, selected: Boolean, available: Int, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (selected) colors.surfaceSelected else Color.Transparent, hover = null) {
        Row(Modifier.padding(horizontal = Spacing.sm + 2.dp, vertical = Spacing.sm), verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Glyph(tab.icon(), if (selected) colors.accent else colors.textSecondary)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Label(tab.title, style = if (selected) Type.bodyMedium else Type.body)
                Label(tab.subtitle, color = colors.textTertiary, style = Type.caption, maxLines = 2)
            }
            if (available > 0) CountBadge(available, tint = colors.accent)
        }
    }
}

/** The middle pane of an area: its groups with a tri-state check over the selectable items, a count of what is available, and a click that jumps to the group. */
@Composable
fun AuthorGroupPane(view: CatalogView, enabled: Boolean, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(if (view.searching) "MATCHES" else "GROUPS", color = colors.textTertiary, style = Type.overline, modifier = Modifier.padding(start = Spacing.xs, bottom = Spacing.xs))
        view.groups.forEach { group -> GroupRow(view, group, enabled) }
    }
}

@Composable
private fun GroupRow(view: CatalogView, group: CatalogGroup, enabled: Boolean) {
    val open = group.entries.filter(CatalogSelection::selectable).map { it.item().id() }
    val check = CatalogSelection.triState(view.selected, open)
    Pressable({ view.focus(group) }, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), hover = null) {
        Row(Modifier.padding(horizontal = Spacing.sm, vertical = Spacing.xs), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (open.isEmpty()) Glyph(Icons.Check, colors.textTertiary, size = 14.dp, modifier = Modifier.padding(horizontal = Spacing.xxs))
            else AppCheckbox(check == Check.ALL, { view.checkAll(open, check != Check.ALL) }, indeterminate = check == Check.SOME, enabled = enabled)
            Label(group.title, Modifier.weight(1f), style = Type.body, tooltipWhenTruncated = true)
            val available = CatalogSelection.available(group)
            if (available > 0) CountBadge(available, tint = colors.accent)
        }
    }
}
