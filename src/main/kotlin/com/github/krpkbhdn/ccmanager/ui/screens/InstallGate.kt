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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.github.krpkbhdn.ccmanager.ui.components.AppIcon
import com.github.krpkbhdn.ccmanager.ui.components.FontSize
import com.github.krpkbhdn.ccmanager.ui.components.Glyph
import com.github.krpkbhdn.ccmanager.ui.components.IconAction
import com.github.krpkbhdn.ccmanager.ui.components.Label
import com.github.krpkbhdn.ccmanager.ui.components.PrimaryButton
import com.github.krpkbhdn.ccmanager.ui.components.cardSurface
import com.github.krpkbhdn.ccmanager.ui.state.installCommands
import com.github.krpkbhdn.ccmanager.ui.theme.Alpha
import com.github.krpkbhdn.ccmanager.ui.theme.Radius
import com.github.krpkbhdn.ccmanager.ui.theme.Spacing
import com.github.krpkbhdn.ccmanager.ui.theme.colors
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection

private val gateWidth = 560.dp
private val gateIconBox = 64.dp
private const val DEFAULT_PROBLEM = "The claude command was not found on this machine."

fun copyToClipboard(text: String) = Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null)

/** Full-window screen shown until the Claude Code CLI is found. */
@Composable
fun InstallGate(problem: String?, checking: Boolean, onCheckAgain: () -> Unit, osName: String, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(colors.background), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(gateWidth).cardSurface(RoundedCornerShape(Radius.lg)).padding(Spacing.xxl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        ) {
            Box(Modifier.size(gateIconBox).clip(CircleShape).background(colors.warning.copy(alpha = Alpha.SOFT)), contentAlignment = Alignment.Center) {
                Glyph(AppIcon.Alert, colors.warning, size = 30.dp)
            }
            Label("Claude Code is not installed", size = FontSize.title,weight = FontWeight.SemiBold)
            Label(problem?.takeIf { it.isNotBlank() } ?: DEFAULT_PROBLEM, color = colors.textSecondary, maxLines = 3)
            Label("Install it with one of these commands, then check again:", color = colors.textSecondary, size = FontSize.small)
            installCommands(osName).forEach { (platform, command) -> CommandRow(platform, command) }
            PrimaryButton(if (checking) "Checking..." else "Check again", onCheckAgain, enabled = !checking, icon = AppIcon.Refresh)
        }
    }
}

@Composable
private fun CommandRow(platform: String, command: String) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        Label(platform, color = colors.textTertiary, size = FontSize.caption, weight = FontWeight.SemiBold)
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Radius.sm)).background(colors.background).padding(start = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Label(command, Modifier.weight(1f), size = FontSize.small, mono = true)
            IconAction(AppIcon.Copy, { copyToClipboard(command) })
        }
    }
}
