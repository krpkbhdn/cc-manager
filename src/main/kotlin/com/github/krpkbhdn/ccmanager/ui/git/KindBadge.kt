package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.git.model.kindLabel
import com.github.krpkbhdn.ccmanager.ui.git.model.kindLetter
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import com.github.krpkbhdn.ccmanager.workspace.GitDiff

private val badgeSize = 18.dp

fun kindOf(change: GitDiff.ChangeType): GitStatus.Kind = when (change) {
    GitDiff.ChangeType.ADDED -> GitStatus.Kind.ADDED
    GitDiff.ChangeType.MODIFIED -> GitStatus.Kind.MODIFIED
    GitDiff.ChangeType.DELETED -> GitStatus.Kind.DELETED
    GitDiff.ChangeType.RENAMED -> GitStatus.Kind.RENAMED
    GitDiff.ChangeType.UNTRACKED -> GitStatus.Kind.UNTRACKED
}

@Composable
fun kindTint(kind: GitStatus.Kind): Color = when (kind) {
    GitStatus.Kind.ADDED, GitStatus.Kind.UNTRACKED -> colors.success
    GitStatus.Kind.MODIFIED -> colors.warning
    GitStatus.Kind.DELETED, GitStatus.Kind.UNMERGED -> colors.danger
    GitStatus.Kind.RENAMED, GitStatus.Kind.COPIED -> colors.accent
    GitStatus.Kind.TYPE_CHANGED, GitStatus.Kind.NONE -> colors.neutral
}

/** Tinted square with the A / M / D / R / U letter of a change; hover names the kind. */
@Composable
fun KindBadge(kind: GitStatus.Kind, modifier: Modifier = Modifier) {
    val tint = kindTint(kind)
    WithTooltip(kindLabel(kind)) {
        Box(modifier.size(badgeSize).background(tint.copy(alpha = Alpha.SOFT), RoundedCornerShape(Radius.xs)), contentAlignment = Alignment.Center) {
            Label(kindLetter(kind), color = tint, style = Type.captionMedium)
        }
    }
}
