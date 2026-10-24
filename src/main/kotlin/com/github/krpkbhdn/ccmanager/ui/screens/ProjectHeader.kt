package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.MenuEntry
import com.github.krpkbhdn.ccmanager.ui.components.PopupMenu
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SecondaryButton
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import org.jetbrains.jewel.ui.component.DefaultButton

private const val IDE_UNAVAILABLE_HINT = "IntelliJ IDEA was not found on this machine"

class ProjectHeaderActions(
    val onLaunch: (LaunchMode) -> Unit,
    val onOpenFolder: () -> Unit,
    val onOpenIde: () -> Unit,
    val onShowDiff: () -> Unit,
)

@Composable
fun ProjectHeader(project: Project, status: AgentStatus, ideAvailable: Boolean, launching: Boolean, actions: ProjectHeaderActions, modifier: Modifier = Modifier) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.xl, vertical = Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                    Label(project.name, size = FontSize.title, weight = FontWeight.SemiBold)
                    StatusChip(status)
                }
                Label(project.path.toString(), color = colors.textTertiary, size = FontSize.small)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton("Open folder", actions.onOpenFolder, icon = AppIcon.Folder)
                SecondaryButton(
                    "Open in IntelliJ IDEA", actions.onOpenIde, icon = AppIcon.Code, enabled = ideAvailable,
                    tooltip = IDE_UNAVAILABLE_HINT.takeUnless { ideAvailable },
                )
                SecondaryButton("Diff", actions.onShowDiff, icon = AppIcon.Diff)
                LaunchSplitButton(launching, actions.onLaunch)
            }
        }
        HorizontalRule()
    }
}

@Composable
private fun LaunchSplitButton(launching: Boolean, onLaunch: (LaunchMode) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        PrimaryButton(if (launching) "Launching..." else "Launch Claude", { onLaunch(LaunchMode.NEW) }, icon = AppIcon.Play, enabled = !launching)
        Spacer(Modifier.width(2.dp))
        Box {
            DefaultButton(onClick = { expanded = true }, enabled = !launching) { Glyph(AppIcon.ChevronDown, colors.onAccent, size = 14.dp) }
            if (expanded) {
                PopupMenu(
                    entries = listOf(
                        MenuEntry("New session") { onLaunch(LaunchMode.NEW) },
                        MenuEntry("Continue last") { onLaunch(LaunchMode.CONTINUE) },
                        MenuEntry("Resume...") { onLaunch(LaunchMode.RESUME_PICKER) },
                    ),
                    onDismiss = { expanded = false },
                    alignEnd = true,
                )
            }
        }
    }
}
