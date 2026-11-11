package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.PresetComponent
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val iconBox = 32.dp

private fun PresetTab.editor(): ComponentEditor = when (this) {
    PresetTab.INSTRUCTIONS -> ComponentEditor.INSTRUCTION
    PresetTab.SKILLS -> ComponentEditor.SKILL_ADD
    PresetTab.PLUGINS -> ComponentEditor.PLUGINS
    PresetTab.MCP -> ComponentEditor.MCP
    PresetTab.AGENTS -> ComponentEditor.AGENT
    PresetTab.FILES -> ComponentEditor.FILE
    PresetTab.SETTINGS -> ComponentEditor.SETTINGS
}

private fun PresetComponentKind.editor(): ComponentEditor? = when (this) {
    PresetComponentKind.INSTRUCTION -> ComponentEditor.INSTRUCTION
    PresetComponentKind.SKILL -> ComponentEditor.SKILL_EDIT
    PresetComponentKind.MCP_SERVER -> ComponentEditor.MCP
    PresetComponentKind.AGENT -> ComponentEditor.AGENT
    PresetComponentKind.FILE -> ComponentEditor.FILE
    PresetComponentKind.SETTINGS -> ComponentEditor.SETTINGS
    PresetComponentKind.PLUGIN, PresetComponentKind.MARKETPLACE -> null
}

internal fun PresetComponentKind.icon(): ImageVector = when (this) {
    PresetComponentKind.INSTRUCTION -> Icons.Log
    PresetComponentKind.SKILL -> Icons.Book
    PresetComponentKind.PLUGIN -> Icons.Plug
    PresetComponentKind.MARKETPLACE -> Icons.Grid
    PresetComponentKind.MCP_SERVER -> Icons.Server
    PresetComponentKind.AGENT -> Icons.Bot
    PresetComponentKind.FILE -> Icons.FileEdit
    PresetComponentKind.SETTINGS -> Icons.Settings
}

private fun PresetTab.reorderable(): Boolean = this == PresetTab.INSTRUCTIONS || this == PresetTab.PLUGINS

/** The cards of one tab with its Add button on top; an empty tab explains itself and offers the same button. */
@Composable
fun ComponentList(state: PresetsState, preset: ProjectPreset, tab: PresetTab, modifier: Modifier = Modifier) {
    val components = tab.componentsOf(preset)
    val add = { state.open(tab.editor(), preset.id()) }
    if (components.isEmpty()) {
        EmptyState(tab.icon(), tab.emptyTitle, tab.emptyHint, modifier) { AppButton(tab.addLabel, add, icon = Icons.Plus) }
        return
    }
    val list = rememberLazyListState()
    Box(modifier) {
        LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.lg, end = Spacing.lg, top = Spacing.xxs, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            item(key = "add") { AddRow(tab, add, canAdd = tab != PresetTab.SETTINGS) }
            val ordered = components.sortedBy { if (it.kind() == PresetComponentKind.MARKETPLACE) 1 else 0 }
            itemsIndexed(ordered, key = { _, component -> component.id() }) { index, component ->
                if (component.kind() == PresetComponentKind.MARKETPLACE && component.id() == ordered.first { it.kind() == PresetComponentKind.MARKETPLACE }.id()) {
                    Label("MARKETPLACES", Modifier.padding(top = Spacing.md, start = Spacing.xs), color = colors.textTertiary, style = Type.overline)
                }
                val peers = ordered.filter { it.kind() == component.kind() }
                val position = peers.indexOfFirst { it.id() == component.id() }
                ComponentCard(
                    component,
                    onEdit = component.kind().editor()?.let { editor -> { state.open(editor, preset.id(), component) } },
                    onRemove = { state.requestRemove(component) },
                    onUp = if (tab.reorderable() && component.kind() != PresetComponentKind.MARKETPLACE) ({ state.move(component, true) }) else null,
                    onDown = if (tab.reorderable() && component.kind() != PresetComponentKind.MARKETPLACE) ({ state.move(component, false) }) else null,
                    first = position == 0,
                    last = position == peers.lastIndex,
                )
            }
        }
        ThinScrollbar(list)
    }
}

private fun PresetTab.icon(): ImageVector = kinds.first().icon()

@Composable
private fun AddRow(tab: PresetTab, onAdd: () -> Unit, canAdd: Boolean) {
    if (!canAdd) return
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Label(tab.emptyHint, Modifier.weight(1f).padding(end = Spacing.md), color = colors.textTertiary, style = Type.caption, maxLines = 2)
        AppButton(tab.addLabel, onAdd, icon = Icons.Plus, compact = true)
    }
}

@Composable
private fun ComponentCard(component: PresetComponent, onEdit: (() -> Unit)?, onRemove: () -> Unit, onUp: (() -> Unit)?, onDown: (() -> Unit)?, first: Boolean, last: Boolean) {
    val line = lineOf(component)
    val shape = RoundedCornerShape(Radius.lg)
    Row(
        Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape).padding(horizontal = Spacing.md, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.md),
    ) {
        Box(Modifier.size(iconBox).clip(RoundedCornerShape(Radius.md)).background(colors.accent.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
            Glyph(component.kind().icon(), colors.accent)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label(line.title, style = Type.bodyMedium, tooltipWhenTruncated = true)
            if (line.secondary.isNotBlank()) Label(line.secondary, color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xxs), verticalAlignment = Alignment.CenterVertically) {
            if (onUp != null && onDown != null) {
                IconButton(Icons.ArrowUp, onUp, tooltip = "Move up", enabled = !first)
                IconButton(Icons.ArrowDown, onDown, tooltip = "Move down", enabled = !last)
            }
            if (onEdit != null) IconButton(Icons.Edit, onEdit, tooltip = "Edit")
            IconButton(Icons.Trash, onRemove, tooltip = "Remove from preset", tint = colors.danger)
        }
    }
}
