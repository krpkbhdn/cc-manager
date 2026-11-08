package com.github.krpkbhdn.ccmanager.ui.screens

import androidx.compose.ui.graphics.vector.ImageVector
import com.github.krpkbhdn.ccmanager.ui.icons.Icons
import com.github.krpkbhdn.ccmanager.ui.state.Route
import com.github.krpkbhdn.ccmanager.ui.state.SidebarSection

class NavEntry(val icon: ImageVector, val label: String, val route: Route)

class NavSection(val section: SidebarSection, val icon: ImageVector, val entries: List<NavEntry>)

internal val navSections = listOf(
    NavSection(
        SidebarSection.LIBRARY, Icons.Book,
        listOf(NavEntry(Icons.Users, "Teams", Route.Teams), NavEntry(Icons.Message, "Prompt Library", Route.Prompts)),
    ),
    NavSection(
        SidebarSection.EXTENSIONS, Icons.Plug,
        listOf(NavEntry(Icons.Plug, "Plugins", Route.Plugins), NavEntry(Icons.Server, "MCP servers", Route.Mcp), NavEntry(Icons.Book, "Skills", Route.Skills)),
    ),
)
