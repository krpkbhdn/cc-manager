package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.HorizontalRule
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Spinner
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

/** Session header: the team of the session (or the one a new launch would use) and its roles; a role with a running subagent shows a spinner, no team shows a muted note. */
@Composable
fun TeamRoleStrip(team: Team?, runningAgentTypes: Set<String>, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth().background(colors.surface).horizontalScroll(rememberScrollState()).padding(horizontal = Spacing.lg, vertical = Spacing.xs + 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        Glyph(Icons.Users, colors.textTertiary)
        Label(team?.name ?: "No team", color = if (team == null) colors.textTertiary else colors.textSecondary, style = Type.smallMedium)
        team?.roles?.forEachIndexed { slot, role -> RolePill(role, slot, role.name() in runningAgentTypes) }
    }
    HorizontalRule()
}

@Composable
private fun RolePill(role: TeamRole, slot: Int, running: Boolean) {
    val shape = RoundedCornerShape(Radius.pill)
    val tint = roleColor(slot)
    Row(
        Modifier.clip(shape).border(1.dp, if (running) tint else colors.border, shape).padding(start = 3.dp, end = Spacing.sm, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp),
    ) {
        RoleAvatar(role.name(), slot, 18.dp)
        Label(role.name(), style = Type.smallMedium)
        Label(roleModelLabel(role), color = colors.textTertiary, style = Type.caption)
        if (running) Spinner(size = 11.dp, tint = tint)
    }
}
