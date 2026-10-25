package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.LaunchedEffect
import com.github.krpkbhdn.ccmanager.ui.components.ThinHorizontalScrollbar
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import androidx.compose.ui.graphics.Color

/** One entry of the tab strip; [status] is null for tabs without a traffic light. */
data class TabSpec(val tab: ProjectTab, val label: String, val status: AgentStatus?, val icon: ImageVector?, val closable: Boolean)

private val pillHeight = 30.dp
private val pillMaxWidth = 220.dp
private val closeSize = 20.dp
private val scrollbarDrop = 8.dp
private val tabScrollbarThickness = 3.dp

/** Pill tabs for sessions, diff and activity; [trailing] holds panel toggles on the right. */
@Composable
fun ProjectTabStrip(
    tabs: List<TabSpec>,
    selected: ProjectTab,
    onSelect: (ProjectTab) -> Unit,
    onClose: (ProjectTab) -> Unit,
    onNewSession: () -> Unit,
    shellMenu: List<MenuEntry>,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier.fillMaxWidth().padding(horizontal = Spacing.lg, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            val scroll = rememberScrollState()
            val closable = tabs.filter { it.closable }
            BoxWithConstraints(Modifier.weight(1f, fill = false)) {
                val rowWidth = maxWidth
                Row(Modifier.wheelScrollsHorizontally(scroll).horizontalScroll(scroll), horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    closable.forEach { spec -> TabPill(spec, spec.tab == selected, { onSelect(spec.tab) }, { onClose(spec.tab) }, rowWidth) }
                }
                if (scroll.maxValue > 0) Box(Modifier.matchParentSize().offset(y = scrollbarDrop)) { ThinHorizontalScrollbar(scroll, tabScrollbarThickness) }
            }
            if (scroll.maxValue > 0) TabOverflowButton(closable, selected, onSelect)
            IconButton(Icons.Plus, onNewSession, Modifier.padding(horizontal = Spacing.xs), tooltip = "Start another session", hint = AppShortcut.NEW_SESSION.hint())
            if (shellMenu.isNotEmpty()) NewShellButton(shellMenu)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                tabs.filterNot { it.closable }.forEach { spec -> TabPill(spec, spec.tab == selected, { onSelect(spec.tab) }, { onClose(spec.tab) }) }
            }
        }
        trailing()
    }
}

@Composable
private fun NewShellButton(menu: List<MenuEntry>) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.padding(end = Spacing.xs)) {
        IconButton(Icons.SquareTerminal, { expanded = true }, tooltip = "Open terminal", hint = AppShortcut.OPEN_TERMINAL.hint())
        if (expanded) PopupMenu(menu, onDismiss = { expanded = false })
    }
}

@Composable
private fun TabPill(spec: TabSpec, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit, maxWidth: Dp = Dp.Infinity) {
    val shape = RoundedCornerShape(Radius.md)
    val tint = if (selected) colors.textPrimary else colors.textSecondary
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(selected) { if (selected) requester.bringIntoView() }
    Pressable(
        onSelect,
        Modifier.bringIntoViewRequester(requester).widthIn(max = maxWidth).height(pillHeight).then(if (selected) Modifier.border(1.dp, colors.borderStrong, shape) else Modifier),
        shape,
        base = if (selected) colors.surfaceRaised else Color.Transparent,
    ) { hovered ->
        Row(
            Modifier.padding(start = Spacing.md - 2.dp, end = if (spec.closable) Spacing.xs else Spacing.md - 2.dp).height(pillHeight),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm - 1.dp),
        ) {
            spec.status?.let { StatusDot(it, size = 7.dp) }
            spec.icon?.let { Glyph(it, tint, size = IconSize.medium) }
            Label(spec.label, Modifier.weight(1f, fill = false).widthIn(max = pillMaxWidth), color = tint, style = if (selected) Type.smallMedium else Type.small, tooltipWhenTruncated = true)
            if (spec.closable) {
                if (hovered || selected) IconButton(Icons.Close, onClose, tint = colors.textTertiary, size = closeSize, tooltip = "Close tab", hint = AppShortcut.CLOSE_SESSION.hint()) else Box(Modifier.size(closeSize))
            }
        }
    }
}
