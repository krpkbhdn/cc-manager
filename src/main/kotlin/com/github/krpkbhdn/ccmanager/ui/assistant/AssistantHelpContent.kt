package com.github.krpkbhdn.ccmanager.ui.assistant

import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut

data class ToolSummary(val name: String, val description: String)

data class ToolGroup(val title: String, val tools: List<ToolSummary>)

data class HotkeyLine(val label: String, val keys: String)

/** Ordered rules: the first matching one names the group of a tool, so a new tool lands in "Other" until a rule is added. */
private val groupRules: List<Pair<String, (String) -> Boolean>> = listOf(
    "Git" to { it.startsWith("git_") },
    "Runs" to { it.endsWith("_run") || it.endsWith("_runs") },
    "Sessions" to { it.endsWith("_session") || it.endsWith("_sessions") || it == "queue_prompt" },
    "Projects" to { it.endsWith("_project") || it.endsWith("_projects") },
    "Groups" to { it.endsWith("_group") },
    "Teams" to { it.endsWith("_team") || it.endsWith("_teams") },
    "Prompts" to { it.endsWith("_prompt") || it.endsWith("_prompts") },
    "Skills & plugins" to { "skill" in it || "plugin" in it || it.endsWith("_marketplace") },
    "MCP servers" to { "mcp_server" in it },
    "UI" to { it == "navigate" },
    "Help" to { it == "get_help" },
)
private const val OTHER_GROUP = "Other"

private val helpShortcuts = listOf(
    AppShortcut.ASSISTANT, AppShortcut.QUICK_REQUEST, AppShortcut.PALETTE, AppShortcut.ALL_SESSIONS, AppShortcut.NEXT_WAITING,
    AppShortcut.ADD_PROJECT, AppShortcut.NEW_SESSION, AppShortcut.RUN, AppShortcut.GIT_TAB, AppShortcut.USAGE,
    AppShortcut.NOTIFICATIONS, AppShortcut.SHORTCUT_SHEET,
)

val assistantExamples = listOf("add project C:\\x and open it", "queue 'run tests' to project X", "what is running right now?",
    "group projects A and B into a group", "install the plugin foo@bar")

/** Groups tools by name in the rule order, tools sorted by name inside a group, empty groups dropped. */
fun groupTools(tools: List<ToolSummary>): List<ToolGroup> {
    val titles = groupRules.map { it.first } + OTHER_GROUP
    val byTitle = tools.sortedBy { it.name }.groupBy { tool -> groupRules.firstOrNull { it.second(tool.name) }?.first ?: OTHER_GROUP }
    return titles.mapNotNull { title -> byTitle[title]?.let { ToolGroup(title, it) } }
}

/** The first sentence of a tool description, enough for a one-line hint. */
fun oneLine(description: String): String = description.trim().lineSequence().first().substringBefore(". ").trimEnd('.')

fun helpHotkeys(mac: Boolean): List<HotkeyLine> = helpShortcuts.map { HotkeyLine(it.label, it.hint(mac)) }
