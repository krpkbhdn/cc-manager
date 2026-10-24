package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** One entry of the tab strip; [status] is null for tabs without a traffic light. */
data class TabSpec(val tab: ProjectTab, val label: String, val status: AgentStatus?, val icon: AppIcon?, val closable: Boolean)

private val tabHeight = 38.dp
private val indicatorHeight = 2.dp

@Composable
fun ProjectTabStrip(tabs: List<TabSpec>, selected: ProjectTab, onSelect: (ProjectTab) -> Unit, onClose: (ProjectTab) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.background(colors.sidebar)) {
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.sm)) {
            tabs.forEach { spec -> TabItem(spec, spec.tab == selected, { onSelect(spec.tab) }, { onClose(spec.tab) }) }
        }
        HorizontalRule()
    }
}

@Composable
private fun TabItem(spec: TabSpec, selected: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val tint = if (selected) colors.textPrimary else colors.textSecondary
    Column(Modifier.width(IntrinsicSize.Max)) {
        Pressable(onSelect, Modifier.height(tabHeight - indicatorHeight), RoundedCornerShape(Spacing.xs)) { hovered ->
            Row(Modifier.fillMaxHeight().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                spec.status?.let { StatusDot(it) }
                spec.icon?.let { Glyph(it, tint, size = 14.dp) }
                Label(spec.label, color = tint, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal, size = FontSize.small)
                if (spec.closable) {
                    if (hovered || selected) IconAction(AppIcon.Close, onClose, Modifier.size(20.dp), tint = colors.textTertiary) else Box(Modifier.size(20.dp))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(indicatorHeight).background(if (selected) colors.accent else Color.Transparent))
    }
}
