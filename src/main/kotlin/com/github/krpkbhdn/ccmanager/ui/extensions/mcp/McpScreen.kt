package com.github.krpkbhdn.ccmanager.ui.extensions.mcp

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionScreen

/** The MCP servers screen of the sidebar: user-wide servers under a title. */
@Composable
fun McpScreen(state: McpState, modifier: Modifier = Modifier) =
    ExtensionScreen("MCP servers", "Programs that give every Claude session of yours extra tools and data.", modifier) { McpPage(ExtensionContext.Global, state, it) }
