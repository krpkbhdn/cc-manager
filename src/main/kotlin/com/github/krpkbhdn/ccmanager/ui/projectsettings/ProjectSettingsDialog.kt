package com.github.krpkbhdn.ccmanager.ui.projectsettings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.ModalHost
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.McpPage
import com.github.krpkbhdn.ccmanager.ui.extensions.mcp.mcpGroups
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginCommandDialog
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.PluginsPage
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.installedEntries
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.SkillsPage
import com.github.krpkbhdn.ccmanager.ui.extensions.skills.skillGroups
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val WINDOW_SHARE = 0.88f
private val navWidth = 216.dp

private fun ProjectSettingsSection.icon(): ImageVector = when (this) {
    ProjectSettingsSection.PLUGINS -> Icons.Plug
    ProjectSettingsSection.MCP -> Icons.Server
    ProjectSettingsSection.SKILLS -> Icons.Book
}

/** IDE-style settings of one project: a panel of sections on the left, the page of the selected one on the right. */
@Composable
fun ProjectSettingsDialog(state: ProjectSettingsState, onClose: () -> Unit) {
    BoxWithConstraints {
        ModalHost({ if (!state.innerDialogOpen) onClose() }, width = maxWidth * WINDOW_SHARE, escapeDismisses = !state.innerDialogOpen) {
            Column(Modifier.height(maxHeight * WINDOW_SHARE)) {
                SettingsHeader(state.context.name, onClose)
                HorizontalRule()
                Row(Modifier.weight(1f)) {
                    SectionPanel(state)
                    VerticalRule()
                    Box(Modifier.weight(1f).fillMaxHeight()) { SectionPage(state) }
                }
            }
        }
        state.plugins.pending?.let { PluginCommandDialog(it, state.plugins.job == null, state.plugins::confirmPending, state.plugins::dismissPending) }
    }
}

@Composable
private fun SettingsHeader(projectName: String, onClose: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = Spacing.xl, end = Spacing.md, top = Spacing.md, bottom = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Glyph(Icons.Settings, colors.textSecondary, size = IconSize.large)
        Label("Project settings — $projectName", Modifier.weight(1f), style = Type.subtitle, tooltipWhenTruncated = true)
        IconButton(Icons.Close, onClose, tooltip = "Close", hint = "Esc")
    }
}

@Composable
private fun SectionPanel(state: ProjectSettingsState) {
    Column(Modifier.width(navWidth).fillMaxHeight().background(colors.sidebar).padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        ProjectSettingsSection.entries.forEach { section ->
            SectionRow(section, state.section == section, sectionCount(state, section)) { state.section = section }
        }
    }
}

@Composable
private fun sectionCount(state: ProjectSettingsState, section: ProjectSettingsSection): Int {
    val context = state.context
    return when (section) {
        ProjectSettingsSection.PLUGINS -> remember(state.plugins.installed) { installedEntries(state.plugins.installed, context).size }
        ProjectSettingsSection.MCP -> remember(state.mcp.servers, state.mcp.health) { mcpGroups(state.mcp.servers, state.mcp.health, context).size }
        ProjectSettingsSection.SKILLS -> remember(state.skills.skills) { skillGroups(state.skills.skills, context).size }
    }
}

@Composable
private fun SectionRow(section: ProjectSettingsSection, selected: Boolean, count: Int, onClick: () -> Unit) {
    val tint: Color = if (selected) colors.accent else colors.textSecondary
    Pressable(onClick, Modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md), base = if (selected) colors.accentSoft else Color.Transparent) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Glyph(section.icon(), tint, size = IconSize.large)
            Label(section.label, Modifier.weight(1f), color = if (selected) colors.textPrimary else colors.textSecondary, style = Type.bodyMedium)
            if (count > 0) CountBadge(count)
        }
    }
}

@Composable
private fun SectionPage(state: ProjectSettingsState) {
    when (state.section) {
        ProjectSettingsSection.PLUGINS -> PluginsPage(state.context, state.plugins)
        ProjectSettingsSection.MCP -> McpPage(state.context, state.mcp)
        ProjectSettingsSection.SKILLS -> SkillsPage(state.context, state.skills)
    }
}
