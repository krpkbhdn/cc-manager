package com.github.krpkbhdn.ccmanager.ui.extensions.plugins

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionScreen

/** The Plugins screen of the sidebar: user-wide plugins under a title. */
@Composable
fun PluginsScreen(state: PluginsState, modifier: Modifier = Modifier) =
    ExtensionScreen("Plugins", "Skills, agents, hooks and MCP servers that every Claude session of yours can use.", modifier) { PluginsPage(ExtensionContext.Global, state, it) }
