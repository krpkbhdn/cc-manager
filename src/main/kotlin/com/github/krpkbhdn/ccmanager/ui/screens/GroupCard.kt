package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val cardMinHeight = 168.dp

/** What a member's mini card shows inside its group card. */
class GroupMemberSummary(val name: String, val status: AgentStatus, val activity: String?, val onOpen: () -> Unit)

/** Overview card of a project group: worst status, member count and one compact card per member project. */
@Composable
fun GroupCard(name: String, status: AgentStatus, members: List<GroupMemberSummary>, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.xl)
    val edge = if (status == AgentStatus.OFFLINE) colors.border else colors.status(status).copy(alpha = Alpha.BORDER_TINT / 2)
    Column(
        modifier.fillMaxWidth().heightIn(min = cardMinHeight).border(1.dp, edge, shape),
        verticalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Pressable(onOpen, Modifier.fillMaxWidth(), shape, colors.surface, colors.surfaceHover) {
            Row(Modifier.fillMaxWidth().padding(Spacing.lg + 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                ProjectAvatar(name, size = 40.dp, cutout = colors.surface)
                Label(name, Modifier.weight(1f), style = Type.subtitle, tooltipWhenTruncated = true)
                CountBadge(members.size)
                StatusChip(status)
            }
        }
        members.forEach { MemberCard(it) }
    }
}

@Composable
private fun MemberCard(member: GroupMemberSummary) {
    Pressable(member.onOpen, Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), RoundedCornerShape(Radius.md), colors.surface, colors.surfaceHover) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatusDot(member.status, size = 8.dp)
            Label(member.name, style = Type.bodyMedium, modifier = Modifier.weight(1f, fill = false), tooltipWhenTruncated = true)
            Label(member.activity ?: "No live sessions", Modifier.weight(1f), color = colors.textTertiary, style = Type.caption, tooltipWhenTruncated = true)
        }
    }
}
