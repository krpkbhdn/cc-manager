package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.Shortcut
import com.github.krpkbhdn.ccmanager.ui.components.StatusChip
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.IconSize
import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val copyWidth = 440.dp
private val artWidth = 300.dp
private val artHeight = 188.dp
private val windowWidth = 236.dp
private val windowHeight = 132.dp
private const val CURSOR_MILLIS = 700

/** First-run screen: a small drawn terminal scene, the pitch and one strong call to action. */
@Composable
fun OverviewEmpty(onAddProject: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(Modifier.padding(Spacing.xl).widthIn(max = copyWidth), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            EmptyIllustration()
            Label("Add your first project", style = Type.title, align = TextAlign.Center)
            Label(
                "Point the manager at a folder and launch Claude in it. Sessions, subagents and approvals show up here the moment they need you.",
                color = colors.textSecondary,
                maxLines = 4,
                align = TextAlign.Center,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
                AppButton("Add project", onAddProject, variant = ButtonVariant.PRIMARY, icon = Icons.FolderPlus)
                Shortcut(AppShortcut.ADD_PROJECT)
            }
            Label("or drop a folder anywhere in this window", color = colors.textTertiary, style = Type.small)
        }
    }
}

@Composable
private fun EmptyIllustration() {
    Box(Modifier.size(artWidth, artHeight), contentAlignment = Alignment.Center) {
        Box(Modifier.size(artWidth, artHeight).background(Brush.radialGradient(listOf(colors.accent.copy(alpha = Alpha.SOFT), Color.Transparent))))
        Box(Modifier.offset(x = 14.dp, y = 12.dp).rotate(4f).size(windowWidth, windowHeight).card(RoundedCornerShape(Radius.lg), colors.surfaceHover))
        TerminalWindow(Modifier.offset(x = (-8).dp, y = 0.dp).rotate(-2f))
        Box(Modifier.align(Alignment.TopEnd).offset(x = (-6).dp, y = 4.dp).rotate(5f)) { StatusChip(AgentStatus.NEEDS_ACTION) }
        Box(Modifier.align(Alignment.BottomStart).offset(x = 2.dp, y = (-6).dp).rotate(-4f)) { StatusChip(AgentStatus.WORKING) }
        Box(Modifier.align(Alignment.BottomEnd).offset(x = (-14).dp, y = 2.dp).rotate(3f)) { StatusChip(AgentStatus.IDLE) }
    }
}

@Composable
private fun TerminalWindow(modifier: Modifier) {
    val cursor by rememberInfiniteTransition(label = "cursor").animateFloat(
        initialValue = 1f, targetValue = 0f,
        animationSpec = infiniteRepeatable(tween(CURSOR_MILLIS), RepeatMode.Reverse), label = "cursorAlpha",
    )
    Column(modifier.size(windowWidth, windowHeight).shadow(22.dp, RoundedCornerShape(Radius.lg), ambientColor = colors.shadow, spotColor = colors.shadow).card(RoundedCornerShape(Radius.lg), colors.terminal)) {
        Row(Modifier.fillMaxWidth().padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.xs + 2.dp)) {
            listOf(colors.danger, colors.warning, colors.success).forEach { Box(Modifier.size(8.dp).background(it, CircleShape)) }
        }
        Column(Modifier.padding(horizontal = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Glyph(Icons.ChevronRight, colors.accent, size = IconSize.medium)
                Box(Modifier.width(96.dp).height(7.dp).clip(CircleShape).background(colors.textPrimary.copy(alpha = 0.55f)))
                Box(Modifier.width(7.dp).height(14.dp).alpha(cursor).background(colors.accent))
            }
            Box(Modifier.fillMaxWidth(0.8f).height(6.dp).clip(CircleShape).background(colors.textTertiary.copy(alpha = 0.3f)))
            Box(Modifier.fillMaxWidth(0.6f).height(6.dp).clip(CircleShape).background(colors.textTertiary.copy(alpha = 0.22f)))
            Box(Modifier.fillMaxWidth(0.45f).height(6.dp).clip(CircleShape).background(colors.success.copy(alpha = 0.4f)))
        }
    }
}
