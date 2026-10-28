package com.github.krpkbhdn.ccmanager.ui.sessions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import com.github.krpkbhdn.ccmanager.ui.components.CountBadge
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.ProjectAvatar
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val groupCardWidth = 320.dp

/** Collapsible project header of the grouped view: avatar, name, session count, and flags for waiting and silent sessions. */
@Composable
fun GroupHeader(group: ProjectSessions, expanded: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    Pressable(onToggle, modifier.fillMaxWidth(), shape = RoundedCornerShape(Radius.md)) {
        Row(
            Modifier.padding(vertical = Spacing.xs, horizontal = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Glyph(if (expanded) Icons.ChevronDown else Icons.ChevronRight, colors.textSecondary)
            ProjectAvatar(group.name, size = 28.dp, cutout = colors.background)
            Label(group.name, style = Type.heading, modifier = Modifier.weight(1f, fill = false))
            CountBadge(group.sessions.size)
            if (group.waiting > 0) CountBadge(group.waiting, tint = colors.danger)
            if (group.stale > 0) Glyph(Icons.Clock, colors.warning)
        }
    }
}

/** One horizontally scrolling row of fixed-width cards, in start order. */
@Composable
fun GroupRow(sessions: List<SessionSnapshot>, card: @Composable (SessionSnapshot, Modifier) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.spacedBy(Spacing.lg),
    ) {
        items(sessions, key = { it.ccmSessionId }) { card(it, Modifier.width(groupCardWidth)) }
    }
}
