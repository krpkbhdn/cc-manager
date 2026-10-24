package com.github.krpkbhdn.ccmanager.ui.teams

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.team.Team
import com.github.krpkbhdn.ccmanager.team.TeamRole
import com.github.krpkbhdn.ccmanager.ui.components.Chip
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.state.avatarGradients
import com.github.krpkbhdn.ccmanager.ui.state.modelLabel
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val INHERITED_MODEL = "Inherits"
private const val DEFAULT_MODEL = "Default model"

/** Roles are coloured by their position in the team, so the roles of one team never share a colour. */
fun roleGradient(slot: Int): Pair<Long, Long> = avatarGradients[Math.floorMod(slot, avatarGradients.size)]

fun roleColor(slot: Int): Color = Color(roleGradient(slot).second)

fun Team.slotOf(role: TeamRole): Int = roles.indexOf(role)

/** The subagent role a hook's agent type refers to; the lead is the main session, never a subagent. */
fun Team.subagentRole(agentType: String?): TeamRole? = subagents().firstOrNull { it.name() == agentType }

/** Just the model, for a chip of its own. */
fun roleModelChip(role: TeamRole): String =
    role.selection().model()?.let(::modelLabel) ?: if (role.lead()) DEFAULT_MODEL else INHERITED_MODEL

/** "Haiku · low"; a role without a model inherits the lead's, the lead without one uses Claude's own default. */
fun roleModelLabel(role: TeamRole): String {
    val selection = role.selection()
    return listOfNotNull(roleModelChip(role), selection.effort()).joinToString(" · ")
}

@Composable
fun RoleAvatar(name: String, slot: Int, size: Dp = 28.dp, modifier: Modifier = Modifier) = ProjectAvatar(name, modifier, size, cutout = colors.surface, gradient = roleGradient(slot))

@Composable
fun RoleChip(role: TeamRole, slot: Int, modifier: Modifier = Modifier) = Chip(roleModelLabel(role), roleColor(slot), modifier)

fun SessionSnapshot?.runningAgentTypes(): Set<String> = this?.subagents.orEmpty().filter { it.running }.map { it.agentType }.toSet()
