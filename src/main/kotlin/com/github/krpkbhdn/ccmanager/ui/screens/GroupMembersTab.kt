package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.EmptyState
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.git.GitSummaryLine
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.AppState
import com.github.krpkbhdn.ccmanager.ui.state.GroupDialogRequest
import com.github.krpkbhdn.ccmanager.ui.state.ProjectTab
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.byUrgency
import com.github.krpkbhdn.ccmanager.ui.state.pathTail
import com.github.krpkbhdn.ccmanager.ui.state.sessionTitle
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Members tab of a project group: each member with its status, live sessions and a way into its own project screen. */
@Composable
fun GroupMembersTab(state: AppState, group: Project, modifier: Modifier = Modifier) {
    val members = state.projects.membersOf(group.id)
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Spacing.xl), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Label("${members.size} member projects", Modifier.weight(1f), color = colors.textSecondary, style = Type.bodyMedium)
            AppButton("Edit group", { state.projects.groupDialog = GroupDialogRequest(group) }, icon = Icons.FolderTree, compact = true)
        }
        if (members.isEmpty()) {
            EmptyState(Icons.FolderTree, "No member projects", "Edit the group to add projects to it.")
        }
        members.forEach { MemberCard(state, it) }
    }
}

@Composable
private fun MemberCard(state: AppState, member: Project) {
    val sessions = byUrgency(state.live.of(member.id))
    val git = state.gitSummaryOf(member.id)
    Column(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xxs),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ProjectAvatar(member.name, size = 32.dp, status = state.live.statusOf(member.id), cutout = colors.background)
            Column(Modifier.weight(1f)) {
                Label(member.name, style = Type.bodyMedium, tooltipWhenTruncated = true)
                if (git != null) GitSummaryLine(git) else Label(pathTail(member.path), color = colors.textTertiary, style = Type.caption)
            }
            StatusChip(state.live.statusOf(member.id))
            AppButton("Open", { state.navigate(Route.Project(member.id)) }, icon = Icons.ArrowRight, compact = true)
        }
        sessions.forEach { SessionLine(it) { openSession(state, member, it) } }
    }
}

private fun openSession(state: AppState, member: Project, session: SessionSnapshot) {
    state.navigate(Route.Project(member.id))
    state.select(member.id, ProjectTab.Session(session.ccmSessionId))
}

@Composable
private fun SessionLine(session: SessionSnapshot, onClick: () -> Unit) {
    Pressable(onClick, Modifier.fillMaxWidth().padding(start = 44.dp), shape = RoundedCornerShape(Radius.md)) {
        Row(Modifier.padding(horizontal = Spacing.md, vertical = Spacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatusDot(session.status)
            Label(sessionTitle(session), Modifier.weight(1f, fill = false), style = Type.body, tooltipWhenTruncated = true)
            session.activity?.let { Label(it, Modifier.weight(1f), color = colors.status(session.status), style = Type.caption) }
        }
    }
}
