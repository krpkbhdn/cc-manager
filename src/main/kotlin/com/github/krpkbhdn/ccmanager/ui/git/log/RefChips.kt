package com.github.krpkbhdn.ccmanager.ui.git.log

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private const val MAX_CHIPS = 3
private val chipIcon = 11.dp

/** Refs of a commit as small chips: HEAD, local branches in the lane colour, remotes neutral, tags amber. */
@Composable
fun RefChips(refs: List<GitLog.Ref>, laneColor: Color, modifier: Modifier = Modifier) {
    if (refs.isEmpty()) return
    val shown = refs.sortedBy { it.kind().ordinal }.take(MAX_CHIPS)
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.xs), verticalAlignment = Alignment.CenterVertically) {
        shown.forEach { RefChip(it, laneColor) }
        if (refs.size > MAX_CHIPS) Label("+${refs.size - MAX_CHIPS}", color = colors.textTertiary, style = Type.numericCaption)
    }
}

@Composable
private fun RefChip(ref: GitLog.Ref, laneColor: Color) {
    val (icon, tint) = when (ref.kind()) {
        GitLog.RefKind.HEAD -> Icons.CircleDot to colors.accent
        GitLog.RefKind.LOCAL_BRANCH -> Icons.Branch to laneColor
        GitLog.RefKind.REMOTE_BRANCH -> Icons.Globe to colors.textSecondary
        GitLog.RefKind.TAG -> Icons.Tag to colors.warning
    }
    ChipBody(icon, ref.name(), tint)
}

@Composable
private fun ChipBody(icon: ImageVector, text: String, tint: Color) {
    val shape = RoundedCornerShape(Radius.sm)
    Row(
        Modifier.background(tint.copy(alpha = Alpha.SOFT), shape).border(1.dp, tint.copy(alpha = Alpha.BORDER_TINT), shape).padding(horizontal = Spacing.xs + 2.dp, vertical = 1.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Spacing.xs),
    ) {
        Glyph(icon, tint, size = chipIcon)
        Label(text, color = tint, style = Type.captionMedium)
    }
}
