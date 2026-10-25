package com.github.krpkbhdn.ccmanager.ui.author

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Every component of the author's setup with its status; installing is always possible, whatever was declined before. */
@Composable
fun AuthorConfigurationDialog(manage: AuthorManage, projectName: String?, state: AuthorProfileState) {
    val (project, user) = manage.entries.partition { it.component() == AuthorComponent.PROJECT_SETUP }
    AuthorDialog("Author configuration", state::closeManage) {
        Section("User", user, state)
        if (project.isNotEmpty()) Section("Project ${projectName.orEmpty()}".trim(), project, state)
        Label(footer(state.sessionProjectName), color = colors.textTertiary, style = Type.caption, maxLines = 2)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) { AppButton("Close", state::closeManage) }
    }
}

private fun footer(project: String?) = if (project == null) "Add a project first, Claude finishes the setup there." else "Claude finishes the setup in $project."

@Composable
private fun Section(title: String, entries: List<AuthorEntry>, state: AuthorProfileState) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Label(title, color = colors.textSecondary, style = Type.smallMedium)
        entries.forEach { EntryRow(it, state) }
    }
}

@Composable
private fun EntryRow(entry: AuthorEntry, state: AuthorProfileState) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xxs)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Label(entry.title(), style = Type.bodyMedium)
                Chip(statusLabel(entry.status()), statusTint(entry.status()))
            }
            Label(entry.summary(), color = colors.textSecondary, style = Type.small, maxLines = 2)
            ReplaceWarning(entry)
        }
        AppButton(
            buttonLabel(entry.status()), { state.install(listOf(entry)) },
            variant = if (entry.status() == AuthorStatus.INSTALLED) ButtonVariant.SECONDARY else ButtonVariant.PRIMARY,
            enabled = !state.busy, compact = true,
        )
    }
}

@Composable
private fun statusTint(status: AuthorStatus) = when (status) {
    AuthorStatus.INSTALLED -> colors.success
    AuthorStatus.UPDATE_AVAILABLE -> colors.accent
    AuthorStatus.NOT_INSTALLED, AuthorStatus.DECLINED -> colors.textTertiary
}
