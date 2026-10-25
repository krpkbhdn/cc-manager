package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.AppTextField
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.extensions.BusyLabel
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionAvatar
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionList
import com.github.krpkbhdn.ccmanager.ui.extensions.listSection
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionFailureStrip
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionToolbar
import com.github.krpkbhdn.ccmanager.ui.extensions.ListCard
import com.github.krpkbhdn.ccmanager.ui.extensions.ListSkeleton
import com.github.krpkbhdn.ccmanager.ui.extensions.ListWithDetail
import com.github.krpkbhdn.ccmanager.ui.extensions.ScopeBadge
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val searchWidth = 280.dp

/** Skills for a [context]; [state] must have been created for the same project. */
@Composable
fun SkillsPage(context: ExtensionContext, state: SkillsState, modifier: Modifier = Modifier) {
    LaunchedEffect(Unit) { state.ensureLoaded() }
    val search = rememberTextFieldState(state.query)
    LaunchedEffect(search) { snapshotFlow { search.text.toString() }.collect { state.query = it } }
    val groups = remember(state.skills, state.query, context) { skillGroups(state.skills, context, state.query) }
    val selected = groups.all.firstOrNull { it.key == state.selected }
    LaunchedEffect(selected?.key) { selected?.let { state.loadText(it) } }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            SkillsToolbar(state, search, groups.size)
            (state.failure ?: state.loadFailure)?.let { ExtensionFailureStrip(it, onDismiss = { if (state.failure != null) state.failure = null else state.loadFailure = null }) }
            Box(Modifier.weight(1f)) {
                ListWithDetail(selected?.let { { SkillDetailPane(it, state) } }) {
                    when {
                        !state.loaded -> ListSkeleton()
                        state.skills.isEmpty() -> EmptySkills(context, state)
                        groups.size == 0 -> EmptyState(Icons.Search, "No skills match", "Nothing matches \"${state.query.trim()}\". Try a word from the skill's name or description.")
                        else -> SkillList(context, state, groups)
                    }
                }
            }
        }
        if (state.creating) NewSkillDialog(context, state)
    }
}

@Composable
private fun SkillsToolbar(state: SkillsState, search: TextFieldState, count: Int) {
    ExtensionToolbar {
        AppTextField(search, Modifier.width(searchWidth), placeholder = "Search skills", icon = Icons.Search)
        Label(if (count == 1) "1 skill" else "$count skills", color = colors.textSecondary, style = Type.small)
        Box(Modifier.weight(1f))
        state.job?.let { BusyLabel(it.label) }
        AppButton("New skill", { state.creating = true }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus, enabled = state.job == null, compact = true)
        IconButton(Icons.Refresh, { state.refresh() }, tooltip = "Refresh", enabled = !state.loading && state.job == null)
    }
}

@Composable
private fun SkillList(context: ExtensionContext, state: SkillsState, groups: SkillGroups) {
    val row: @Composable (SkillEntry) -> Unit = { SkillRow(it, state) }
    ExtensionList {
        if (context is ExtensionContext.ForProject && groups.own.isEmpty() && state.query.isBlank()) item(key = "none") {
            Label("No skills of its own in ${context.name} yet. Skills you create for the project show up here.", Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs), color = colors.textTertiary, maxLines = 2)
        }
        items(groups.own, key = { it.key }) { row(it) }
        listSection("inherited", "Inherited from user", groups.inherited, { it.key }, row)
        listSection("plugins", "From plugins", groups.plugins, { it.key }, row)
    }
}

@Composable
private fun SkillRow(entry: SkillEntry, state: SkillsState) {
    val skill = entry.skill
    ListCard(selected = state.selected == entry.key, onClick = { state.selected = entry.key }) {
        ExtensionAvatar(skill.name)
        Column(Modifier.weight(1f)) {
            Label(skill.name, style = Type.bodyMedium, tooltipWhenTruncated = true)
            Label(skill.description.ifBlank { "No description" }, color = colors.textTertiary, style = Type.small, maxLines = 2)
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (skill.readOnly()) Chip(skill.pluginId, colors.neutral) else ScopeBadge(skill.scope)
            if (entry.removable) IconButton(Icons.Trash, { state.requestDelete(entry) }, tooltip = "Delete", tint = colors.danger, enabled = state.job == null)
        }
    }
}

@Composable
private fun EmptySkills(context: ExtensionContext, state: SkillsState) {
    val where = if (context is ExtensionContext.ForProject) "in ${context.name}" else "yet"
    EmptyState(Icons.Book, "No skills $where", "A skill is a folder with a SKILL.md that teaches Claude a task. Claude loads it when the description fits what you ask.") {
        AppButton("New skill", { state.creating = true }, variant = ButtonVariant.PRIMARY, icon = Icons.Plus)
    }
}
