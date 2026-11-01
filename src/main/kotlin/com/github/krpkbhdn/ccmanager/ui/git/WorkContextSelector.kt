package com.github.krpkbhdn.ccmanager.ui.git

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AnchoredPopup
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Pressable
import com.github.krpkbhdn.ccmanager.ui.components.StatusDot
import com.github.krpkbhdn.ccmanager.ui.components.WithTooltip
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import com.github.krpkbhdn.ccmanager.ui.git.model.contextSelectorLabel
import com.github.krpkbhdn.ccmanager.ui.git.model.contextTitle
import com.github.krpkbhdn.ccmanager.ui.git.model.switchTarget
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.WorkContext
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val selectorHeight = 28.dp
private val selectorMaxWidth = 220.dp
private val popupWidth = 360.dp
private val rowHeight = 32.dp
private val dirtyDotSize = 7.dp

/** Toolbar button naming the active folder (main or a worktree); opens the list of every place to work in the project. */
@Composable
fun WorkContextSelector(git: GitState, work: WorkContextActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(Radius.md)
    Box(modifier) {
        Pressable({ git.contextPopupOpen = true }, Modifier.height(selectorHeight).border(1.dp, colors.borderStrong, shape), shape, base = colors.surfaceRaised, hover = colors.surfaceHover) {
            Row(Modifier.padding(horizontal = Spacing.md - 2.dp).height(selectorHeight), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm - 1.dp)) {
                Glyph(Icons.FolderTree, colors.accent, size = IconSize.medium)
                Label(contextSelectorLabel(work.contexts), Modifier.widthIn(max = selectorMaxWidth), color = colors.textPrimary, style = Type.smallMedium, tooltipWhenTruncated = true)
                Glyph(Icons.ChevronsUpDown, colors.textTertiary, size = IconSize.medium)
            }
        }
        if (git.contextPopupOpen) {
            AnchoredPopup({ git.contextPopupOpen = false }, modifier = Modifier.width(popupWidth)) {
                Column(Modifier.padding(Spacing.xs)) {
                    work.contexts.forEach { ContextRow(it, work) { git.contextPopupOpen = false; work.switchTo(it.switchTarget()) } }
                }
            }
        }
    }
}

@Composable
private fun ContextRow(context: WorkContext, work: WorkContextActions, onPick: () -> Unit) {
    val shape = RoundedCornerShape(Radius.sm)
    val session = context.sessionId?.let(work.sessionOf)
    Pressable(onPick, Modifier.fillMaxWidth().height(rowHeight), shape, base = if (context.active) colors.surfaceHover else colors.surfaceRaised, hover = colors.surfaceHover) {
        Row(Modifier.fillMaxWidth().padding(horizontal = Spacing.md), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            if (context.active) Glyph(Icons.Check, colors.accent, size = IconSize.medium) else Box(Modifier.size(IconSize.medium))
            Label(contextTitle(context), Modifier.weight(1f), color = colors.textPrimary, style = Type.monoSmall, tooltipWhenTruncated = true)
            if (context.dirty) WithTooltip("Uncommitted changes") { Box(Modifier.size(dirtyDotSize).border(1.5.dp, colors.warning, CircleShape)) }
            session?.let { WithTooltip("${it.title}: ${it.status.name.lowercase().replace('_', ' ')}") { StatusDot(it.status, size = dirtyDotSize) } }
        }
    }
}
