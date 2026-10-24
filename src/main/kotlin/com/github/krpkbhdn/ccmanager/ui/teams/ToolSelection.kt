package com.github.krpkbhdn.ccmanager.ui.teams

import com.github.krpkbhdn.ccmanager.team.RoleDraft

/** Claude Code's built-in tools a subagent can use (tools-reference, claude 2.1.310), grouped for the picker. */
enum class ToolGroup(val title: String, val tools: List<String>) {
    READ_ONLY("Read-only", listOf("Read", "Glob", "Grep", "LSP")),
    EDIT("Edit", listOf("Write", "Edit", "NotebookEdit")),
    RUN("Run", listOf("Bash", "PowerShell")),
    WEB("Web", listOf("WebFetch", "WebSearch")),
    OTHER("Other", listOf("Agent", "Skill", "TodoWrite")),
}

val builtInTools: List<String> = ToolGroup.entries.flatMap { it.tools }

/** One-click tool sets; [tools] null is "inherit all". */
enum class ToolPreset(val label: String, val tools: List<String>?) {
    INHERIT_ALL("Inherit all", null),
    READ_ONLY("Read-only", listOf("Read", "Glob", "Grep")),
    READ_AND_EDIT("Read & edit", listOf("Read", "Glob", "Grep", "Write", "Edit", "NotebookEdit")),
    FULL_NO_WEB("Full, no web", listOf("Read", "Glob", "Grep", "Write", "Edit", "NotebookEdit", "Bash")),
}

private const val EMPTY_MESSAGE = "Pick at least one tool, or choose Inherit all."

/**
 * The tools of a role: null inherits everything the lead has, otherwise an allowlist of built-in names and custom
 * entries such as `mcp__server__*`. Built-ins keep catalog order, custom entries follow in the order they were added.
 */
data class ToolSelection(val tools: List<String>?) {

    val inheritsAll: Boolean get() = tools == null

    /** While inheriting everything every built-in counts as ticked. */
    fun allows(tool: String): Boolean = tools == null || tool in tools

    fun toggle(tool: String): ToolSelection {
        val current = tools ?: builtInTools
        return ToolSelection(ordered(if (tool in current) current - tool else current + tool))
    }

    fun withPreset(preset: ToolPreset): ToolSelection = ToolSelection(preset.tools)

    val activePreset: ToolPreset? get() = ToolPreset.entries.firstOrNull { it.tools?.toSet() == tools?.toSet() }

    val custom: List<String> get() = tools.orEmpty().filter { it !in builtInTools }

    /** @throws IllegalArgumentException with a user-readable message for an entry claude would not take */
    fun addCustom(entry: String): ToolSelection {
        val tool = entry.trim()
        require(RoleDraft.isToolEntry(tool)) { "Use a tool name such as Read, or mcp__server__* for all tools of an MCP server." }
        return if (tool in (tools ?: builtInTools)) this else ToolSelection(ordered((tools ?: builtInTools) + tool))
    }

    val summary: String
        get() = when (tools?.size) {
            null -> "All tools"
            0 -> "No tools"
            1 -> "1 tool"
            else -> "${tools.size} tools"
        }

    val error: String? get() = EMPTY_MESSAGE.takeIf { tools?.isEmpty() == true }

    /** The stored form: a comma list, or null when everything is inherited. */
    fun serialize(): String? = tools?.joinToString(", ")

    private fun ordered(list: List<String>): List<String> = builtInTools.filter { it in list } + list.filter { it !in builtInTools }

    companion object {
        val INHERIT_ALL = ToolSelection(null)

        fun parse(stored: String?): ToolSelection =
            ToolSelection(stored?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.takeIf { it.isNotEmpty() })
    }
}
