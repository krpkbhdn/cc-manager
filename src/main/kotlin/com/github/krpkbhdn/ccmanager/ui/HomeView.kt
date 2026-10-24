package com.github.krpkbhdn.ccmanager.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text

private val aboutLines = listOf(
    "Cross-platform desktop manager for local Claude Code sessions (Windows, Linux, macOS).",
    "Run sessions, agents and projects from one place, get notifications, review and approve agent proposals.",
    "Planned: a single coordinating Claude that routes requests to project agents, and triggers (GLPI, Jira).",
    "Everything runs locally against your own Claude Code installation - no server-side sessions.",
    "Stack: Java 25, Spring Boot, Compose Multiplatform + Jewel.",
)

@Composable
fun HomeView() {
    Column(
        modifier = Modifier.fillMaxSize().background(JewelTheme.globalColors.panelBackground).padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Hello World", fontSize = 32.sp)
        Text("Claude Code Manager", fontSize = 18.sp)
        aboutLines.forEach { Text(it) }
    }
}
