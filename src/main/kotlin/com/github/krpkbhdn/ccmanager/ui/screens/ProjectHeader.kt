package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.CopyChip
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.IconButton
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.git.GitSummaryChip
import com.github.krpkbhdn.ccmanager.ui.git.model.GitSummary
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.SplitButton
import com.github.krpkbhdn.ccmanager.ui.components.SplitActionButton
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.run.RunControl
import com.github.krpkbhdn.ccmanager.ui.run.RunControlActions
import com.github.krpkbhdn.ccmanager.ui.run.RunEntry
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val IDE_UNAVAILABLE_HINT = "IntelliJ IDEA was not found on this machine"
private const val IDE_HINT = "Open in IntelliJ IDEA"

class ProjectHeaderActions(
    val onLaunch: (LaunchMode) -> Unit,
    val onStartTask: () -> Unit,
    val onStartTaskFromTemplate: () -> Unit,
    val onOpenFolder: () -> Unit,
    val onOpenIde: () -> Unit,
    val onShowGit: () -> Unit,
    val onShowChanges: () -> Unit,
    val onOpenSettings: () -> Unit,
)

/** Compact project header: identity and copyable path on the left, icon actions and the launch split button on the right. */
@Composable
fun ProjectHeader(project: Project, status: AgentStatus, ideAvailable: Boolean, launching: Boolean, gitSummary: GitSummary?, actions: ProjectHeaderActions, runEntries: List<RunEntry>, runSelected: RunEntry?, runActions: RunControlActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            ProjectAvatar(project.name, size = 38.dp, status = status, cutout = colors.background)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Label(project.name, Modifier.weight(1f, fill = false), style = Type.title, tooltipWhenTruncated = true)
                    StatusChip(status)
                    gitSummary?.let { GitSummaryChip(it, actions.onShowGit) }
                }
                CopyChip(project.path.toString(), label = "Copy path")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
                IconButton(Icons.FolderOpen, actions.onOpenFolder, tooltip = "Open folder")
                IconButton(Icons.Code, actions.onOpenIde, tooltip = if (ideAvailable) IDE_HINT else IDE_UNAVAILABLE_HINT, enabled = ideAvailable)
                IconButton(Icons.Settings, actions.onOpenSettings, tooltip = "Project settings", hint = AppShortcut.PROJECT_SETTINGS.hint())
                IconButton(Icons.Diff, actions.onShowChanges, tooltip = "Review changes", hint = AppShortcut.GIT_CHANGES.hint())
            }
            RunControl(runEntries, runSelected, runActions)
            SplitActionButton("Start task", Icons.Users, actions.onStartTask, tooltip = "Run a lead session with one of your agent teams", onChevron = actions.onStartTaskFromTemplate)
            SplitButton(
                text = "Launch Claude",
                icon = Icons.Play,
                onClick = { actions.onLaunch(LaunchMode.NEW) },
                menu = listOf(
                    MenuEntry("New session", Icons.Plus) { actions.onLaunch(LaunchMode.NEW) },
                    MenuEntry("Continue last", Icons.ArrowRight) { actions.onLaunch(LaunchMode.CONTINUE) },
                    MenuEntry("Resume...", Icons.Rewind) { actions.onLaunch(LaunchMode.RESUME_PICKER) },
                ),
                loading = launching,
            )
        }
        HorizontalRule()
    }
}
