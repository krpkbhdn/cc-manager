package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppButton
import com.github.krpkbhdn.ccmanager.ui.components.ButtonVariant
import com.github.krpkbhdn.ccmanager.ui.components.CopyChip
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.card
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.installCommands
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.Type
import com.github.krpkbhdn.ccmanager.ui.theme.colors

private val gateWidth = 560.dp
private val markSize = 56.dp
private val stepBadge = 24.dp
private const val DEFAULT_PROBLEM = "The claude command was not found on this machine."

/** Full-window onboarding shown until the Claude Code CLI is found: install, sign in, check again. */
@Composable
fun InstallGate(problem: String?, checking: Boolean, onCheckAgain: () -> Unit, osName: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(Modifier.width(gateWidth).padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Spacing.lg)) {
            Box(
                Modifier.size(markSize).clip(RoundedCornerShape(Radius.xl)).background(Brush.verticalGradient(listOf(colors.accentHover, colors.accent))),
                contentAlignment = Alignment.Center,
            ) { Glyph(Icons.Terminal, colors.onAccent, size = 28.dp) }
            Label("Install Claude Code to get started", style = Type.title, align = TextAlign.Center)
            Label(problem?.takeIf { it.isNotBlank() } ?: DEFAULT_PROBLEM, color = colors.textSecondary, maxLines = 3, align = TextAlign.Center)
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                StepCard(1, "Install the CLI", "Run the command for your system in a terminal.") {
                    installCommands(osName).forEachIndexed { index, (platform, command) -> CommandBlock(platform, command, primary = index == 0) }
                }
                StepCard(2, "Sign in once", "Run claude in any folder and follow the prompts. The manager reuses that login.") {
                    CommandBlock(null, "claude", primary = true)
                }
                StepCard(3, "Come back here", "Check again and your projects will be waiting.") {
                    Row { AppButton(if (checking) "Checking..." else "Check again", onCheckAgain, variant = ButtonVariant.PRIMARY, icon = Icons.Refresh, loading = checking) }
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, hint: String, content: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth().card(RoundedCornerShape(Radius.lg)).padding(Spacing.lg), horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
        Box(Modifier.size(stepBadge).clip(CircleShape).background(colors.accentSoft), contentAlignment = Alignment.Center) {
            Label(number.toString(), color = colors.accent, style = Type.numeric)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            Label(title, style = Type.heading)
            Label(hint, color = colors.textSecondary, style = Type.small, maxLines = 2)
            content()
        }
    }
}

@Composable
private fun CommandBlock(platform: String?, command: String, primary: Boolean) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        platform?.let { Label(it, color = if (primary) colors.textSecondary else colors.textTertiary, style = Type.captionMedium) }
        CopyChip(command, Modifier.fillMaxWidth(), style = Type.monoBody, label = "Copy command")
    }
}
