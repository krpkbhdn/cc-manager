package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.SkeletonBlock
import com.github.krpkbhdn.ccmanager.ui.components.ThinScrollbar
import com.github.krpkbhdn.ccmanager.ui.extensions.DetailPaneFrame
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.extensions.description
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** One skill: its front matter fields, where it lives and the rendered SKILL.md. */
@Composable
fun SkillDetailPane(entry: SkillEntry, state: SkillsState, modifier: Modifier = Modifier) {
    val skill = entry.skill
    val host = state.host
    val scroll = rememberScrollState()
    DetailPaneFrame(
        skill.name, skill.name, if (skill.readOnly()) skill.pluginId else skill.scope.description(), { state.selected = null }, modifier,
        badges = {
            if (skill.readOnly()) Chip("Plugin", colors.neutral) else ScopeBadge(skill.scope)
            if (entry.inherited) Label("Managed in your user skills", color = colors.textTertiary, style = Type.caption)
        },
        actions = {
            IconButton(Icons.FolderOpen, { host.openFolder(skill.directory()) }, tooltip = "Open folder")
            IconButton(Icons.Code, { host.openIde(skill.directory()) }, tooltip = "Open in IDE", enabled = host.ideAvailable())
            if (entry.removable) IconButton(Icons.Trash, { state.requestDelete(entry) }, tooltip = "Delete skill", tint = colors.danger, enabled = state.job == null)
        },
    ) {
        Box {
            Column(Modifier.fillMaxWidth().verticalScroll(scroll).padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
                Fields(entry)
                HorizontalRule()
                Content(state.texts[entry.key])
            }
            ThinScrollbar(scroll)
        }
    }
}

@Composable
private fun Fields(entry: SkillEntry) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Field("name", entry.skill.name)
        Field("description", entry.skill.description.ifBlank { "No description" })
        Field("file", entry.skill.file.toString(), mono = true)
    }
}

@Composable
private fun Field(label: String, value: String, mono: Boolean = false) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
        Label(label, color = colors.textTertiary, style = Type.caption)
        Label(value, color = colors.textPrimary, style = if (mono) Type.monoSmall else Type.body, maxLines = Int.MAX_VALUE)
    }
}

@Composable
private fun Content(text: SkillText?) {
    when (text) {
        null, SkillText.Loading -> Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SkeletonBlock(Modifier.fillMaxWidth().height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth(0.7f).height(20.dp))
            SkeletonBlock(Modifier.fillMaxWidth().height(80.dp), RoundedCornerShape(Radius.lg))
        }
        is SkillText.Failed -> Label("Cannot show SKILL.md: ${text.message}", color = colors.textTertiary, style = Type.small, maxLines = 4)
        is SkillText.Loaded -> MarkdownView(skillBody(text.content))
    }
}
