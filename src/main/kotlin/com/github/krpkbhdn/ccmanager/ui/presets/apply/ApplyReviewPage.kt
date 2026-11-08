package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.preset.apply.ChangeAction
import com.github.krpkbhdn.ccmanager.preset.apply.PlannedChange
import com.github.krpkbhdn.ccmanager.ui.components.AppCheckbox
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.presets.icon
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val previewMaxHeight = 200.dp
private val fadeHeight = 28.dp

/** Step 2: the plan grouped by kind, each change with a checkbox; a spinner while it is worked out. */
@Composable
fun ApplyReviewPage(state: ApplyPresetState) {
    val plan = state.plan
    when {
        plan == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                Spinner(size = 18.dp)
                Label("Checking the project...", color = colors.textSecondary)
            }
        }
        plan.isEmpty() -> EmptyState(Icons.Tasks, if (state.error == null) "Nothing to apply" else "Could not check the project", if (state.error == null) "This preset has no components yet." else "Go back and try again.")
        else -> PlanList(state, plan)
    }
}

@Composable
private fun PlanList(state: ApplyPresetState, plan: List<PlannedChange>) {
    val list = rememberLazyListState()
    val groups = groupChanges(plan)
    Column(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Label(state.preset?.name().orEmpty(), style = Type.bodyMedium, tooltipWhenTruncated = true)
            Label(planSummary(plan).line(), color = colors.textSecondary, style = Type.small)
        }
        Box(Modifier.weight(1f).fillMaxWidth()) {
            LazyColumn(Modifier.fillMaxSize(), list, contentPadding = PaddingValues(start = Spacing.xl, end = Spacing.xl, bottom = Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                groups.forEach { group ->
                    item(key = "group-${group.kind}") { GroupHeader(group) }
                    items(group.changes.size, key = { group.changes[it].componentId() }) { index ->
                        val change = group.changes[index]
                        ChangeRow(change, change.componentId() in state.selection, change.componentId() in state.expanded, { state.toggle(change) }, { state.toggleExpanded(change.componentId()) })
                    }
                }
            }
            ThinScrollbar(list)
        }
    }
}

@Composable
private fun GroupHeader(group: ChangeGroup) {
    Row(Modifier.padding(top = Spacing.sm, start = Spacing.xs), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
        Glyph(group.kind.icon(), colors.textTertiary, size = 14.dp)
        Label(group.title.uppercase(), color = colors.textTertiary, style = Type.overline)
    }
}

@Composable
private fun actionTint(action: ChangeAction): Color = when (action) {
    ChangeAction.CREATE, ChangeAction.UPDATE, ChangeAction.INSTALL -> colors.accent
    ChangeAction.PRESENT -> colors.neutral
    ChangeAction.CONFLICT -> colors.danger
    ChangeAction.NEEDS_CONFIRMATION -> colors.warning
}

@Composable
private fun ChangeRow(change: PlannedChange, checked: Boolean, expanded: Boolean, onToggle: () -> Unit, onExpand: () -> Unit) {
    val shape = RoundedCornerShape(Radius.lg)
    val present = change.action() == ChangeAction.PRESENT
    val preview = change.preview()
    Column(Modifier.fillMaxWidth().background(colors.surface, shape).border(1.dp, colors.border, shape)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            AppCheckbox(checked, { onToggle() }, enabled = isSelectable(change))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
                Label(change.title(), color = if (present) colors.textTertiary else colors.textPrimary, style = Type.bodyMedium, tooltipWhenTruncated = true)
                if (!change.detail().isNullOrBlank()) Label(change.detail(), color = if (change.action() == ChangeAction.CONFLICT) colors.danger else colors.textTertiary, style = Type.caption, maxLines = 2, tooltipWhenTruncated = true)
            }
            Chip(actionLabel(change.action()), actionTint(change.action()))
            if (preview != null) IconButton(if (expanded) Icons.ChevronDown else Icons.ChevronRight, onExpand, tooltip = if (expanded) "Hide the new text" else "Show the new text")
        }
        if (expanded && preview != null) PreviewText(preview)
    }
}

@Composable
private fun PreviewText(text: String) {
    val scroll = rememberScrollState()
    val shape = RoundedCornerShape(Radius.sm)
    Box(Modifier.fillMaxWidth().padding(start = Spacing.md, end = Spacing.md, bottom = Spacing.md).heightIn(max = previewMaxHeight).background(colors.background, shape).border(1.dp, colors.border, shape)) {
        Label(text, Modifier.fillMaxWidth().verticalScroll(scroll).padding(Spacing.sm), color = colors.textSecondary, style = Type.monoSmall, maxLines = Int.MAX_VALUE)
        if (scroll.canScrollForward) {
            Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(fadeHeight).clip(shape).background(Brush.verticalGradient(listOf(Color.Transparent, colors.background))))
        }
        ThinScrollbar(scroll)
    }
}
