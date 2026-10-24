package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.ContextMenuArea
import androidx.compose.foundation.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.state.RenameRequest
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation
import com.github.krpkbhdn.ccmanager.claude.install.LoginState
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pill
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.components.SectionHeader
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.VerticalRule
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.pathTail
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val sidebarWidth = 272.dp
private val logoSize = 28.dp

@Composable
fun Sidebar(state: AppState, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxHeight()) {
        Column(Modifier.width(sidebarWidth).fillMaxHeight().background(colors.sidebar)) {
            AppHeader()
            Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OverviewRow(state.route == Route.Overview, state.live.sessions.size) { state.navigate(Route.Overview) }
                PrimaryButton("Add project", state::addProject, Modifier.fillMaxWidth(), icon = AppIcon.Plus)
                state.addError?.let { Label(it, color = colors.danger, size = FontSize.small, maxLines = 4) }
            }
            SectionHeader("Projects", Modifier.padding(start = Spacing.lg, end = Spacing.lg, top = Spacing.lg, bottom = Spacing.sm), state.projects.size)
            ProjectList(state, Modifier.weight(1f))
            state.installation?.let { InstallationChip(it) }
        }
        VerticalRule()
    }
}

@Composable
private fun AppHeader() {
    Row(Modifier.padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.size(logoSize).clip(RoundedCornerShape(Radius.sm)).background(colors.accent), contentAlignment = Alignment.Center) {
            Glyph(AppIcon.Terminal, colors.onAccent, size = 16.dp)
        }
        Label("Claude Code Manager", size = FontSize.heading, weight = FontWeight.SemiBold)
    }
}

@Composable
private fun OverviewRow(selected: Boolean, liveSessions: Int, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth(), base = if (selected) colors.surfaceSelected else Color.Transparent) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            Glyph(AppIcon.Home, if (selected) colors.accent else colors.textSecondary)
            Label("Overview", Modifier.weight(1f), weight = FontWeight.Medium)
            if (liveSessions > 0) Pill("$liveSessions live", colors.textSecondary)
        }
    }
}

@Composable
private fun ProjectList(state: AppState, modifier: Modifier) {
    LazyColumn(modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(state.projects, key = { it.id }) { project ->
            ProjectRow(
                project = project,
                status = state.live.statusOf(project.id),
                sessionCount = state.live.of(project.id).size,
                selected = state.route == Route.Project(project.id),
                actions = ProjectActions(
                    onOpen = { state.navigate(Route.Project(project.id)) },
                    onRename = { state.renaming = RenameRequest(project) },
                    onRemove = { state.requestRemove(project) },
                    onOpenFolder = { state.openFolder(project) },
                ),
            )
        }
    }
}

class ProjectActions(val onOpen: () -> Unit, val onRename: () -> Unit, val onRemove: () -> Unit, val onOpenFolder: () -> Unit)

@Composable
private fun ProjectRow(project: Project, status: AgentStatus, sessionCount: Int, selected: Boolean, actions: ProjectActions) {
    ContextMenuArea(items = {
        listOf(
            ContextMenuItem("Open folder", actions.onOpenFolder),
            ContextMenuItem("Rename", actions.onRename),
            ContextMenuItem("Remove", actions.onRemove),
        )
    }) {
        Pressable(actions.onOpen, Modifier.fillMaxWidth(), base = if (selected) colors.surfaceSelected else Color.Transparent) { hovered ->
            Row(Modifier.padding(start = Spacing.md, end = Spacing.sm, top = Spacing.sm, bottom = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StatusDot(status)
                Column(Modifier.weight(1f)) {
                    Label(project.name, weight = FontWeight.Medium)
                    Label(pathTail(project.path), color = colors.textTertiary, size = FontSize.caption)
                }
                if (hovered) RowActions(actions) else if (sessionCount > 0) Pill(sessionCount.toString(), colors.status(status))
            }
        }
    }
}

@Composable
private fun RowActions(actions: ProjectActions) {
    Row {
        IconAction(AppIcon.Folder, actions.onOpenFolder)
        IconAction(AppIcon.Edit, actions.onRename)
        IconAction(AppIcon.Trash, actions.onRemove, tint = colors.danger)
    }
}

@Composable
private fun InstallationChip(installation: ClaudeInstallation) {
    val signedOut = installation.login == LoginState.LOGGED_OUT
    Column {
        HorizontalRule()
        Row(Modifier.fillMaxWidth().padding(Spacing.lg), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatusDot(if (signedOut) AgentStatus.WORKING else AgentStatus.IDLE)
            Column {
                Label("Claude Code ${installation.version ?: ""}".trim(), size = FontSize.small, weight = FontWeight.Medium)
                Label(loginText(installation.login), color = colors.textTertiary, size = FontSize.caption)
            }
            Spacer(Modifier.weight(1f))
        }
    }
}

private fun loginText(login: LoginState): String = when (login) {
    LoginState.LOGGED_IN -> "Signed in"
    LoginState.LOGGED_OUT -> "Signed out"
    LoginState.UNKNOWN -> "Login state unknown"
}
