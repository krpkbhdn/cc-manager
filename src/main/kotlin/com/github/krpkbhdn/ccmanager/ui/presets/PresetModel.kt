package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.preset.PresetComponent
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.AGENT
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.FILE
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.INSTRUCTION
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.MARKETPLACE
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.MCP_SERVER
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.PLUGIN
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.SETTINGS
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind.SKILL
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset

/** The tabs of the preset pane; marketplaces live under Plugins because a plugin is useless without its marketplace. */
enum class PresetTab(val label: String, val kinds: List<PresetComponentKind>, val addLabel: String, val emptyTitle: String, val emptyHint: String) {
    INSTRUCTIONS("Instructions", listOf(INSTRUCTION), "Add instruction", "No instructions", "Rules for Claude that are written into the project's CLAUDE.md."),
    SKILLS("Skills", listOf(SKILL), "Add skill", "No skills", "Skills Claude can use in the project, copied into its .claude/skills folder."),
    PLUGINS("Plugins", listOf(PLUGIN, MARKETPLACE), "Add plugins", "No plugins", "Plugins installed for the project, with the marketplaces they come from."),
    MCP("MCP", listOf(MCP_SERVER), "Add MCP server", "No MCP servers", "Servers that give Claude extra tools, registered for the project."),
    AGENTS("Agents", listOf(AGENT), "Add agent", "No agents", "Subagents Claude can hand work to, copied into the project's .claude/agents folder."),
    FILES("Files", listOf(FILE), "Add file", "No files", "Files created in the project, optionally with lines added to its .gitignore."),
    SETTINGS("Settings", listOf(PresetComponentKind.SETTINGS), "Add settings", "No settings", "Claude Code settings merged into the project's .claude/settings.json."),
    ;

    fun componentsOf(preset: ProjectPreset): List<PresetComponent> = preset.components().filter { it.kind() in kinds }

    /** Marketplaces are not counted: they are a detail of the plugins. */
    fun countIn(preset: ProjectPreset): Int = if (this == PresetTab.SETTINGS) 0 else preset.components().count { it.kind() == kinds.first() }
}

private val summaryOrder = listOf(
    Triple(INSTRUCTION, "instruction", "instructions"),
    Triple(SKILL, "skill", "skills"),
    Triple(PLUGIN, "plugin", "plugins"),
    Triple(MCP_SERVER, "MCP server", "MCP servers"),
    Triple(AGENT, "agent", "agents"),
    Triple(FILE, "file", "files"),
)

/** A short count line such as "4 instructions · 2 skills · 1 file"; "Empty" when nothing is in the preset. */
fun summaryOf(components: List<PresetComponent>): String {
    val parts = summaryOrder.mapNotNull { (kind, one, many) ->
        components.count { it.kind() == kind }.takeIf { it > 0 }?.let { "$it ${if (it == 1) one else many}" }
    } + listOfNotNull("settings".takeIf { components.any { it.kind() == SETTINGS } })
    return if (parts.isEmpty()) "Empty" else parts.joinToString(SEPARATOR)
}

const val SEPARATOR = " · "

/** The presets whose name or description contains every word of [query]; all of them for a blank query. */
fun searchPresets(presets: List<ProjectPreset>, query: String): List<ProjectPreset> {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    if (words.isEmpty()) return presets
    return presets.filter { preset ->
        val haystack = (preset.name() + " " + preset.description()).lowercase()
        words.all { it in haystack }
    }
}

/** The one chip a list row may carry; a newer shipped version outranks the plain "Modified". */
fun chipOf(preset: ProjectPreset): String? = when {
    preset.defaultChanged() -> "Update available"
    preset.edited() -> "Modified"
    else -> null
}

/** A name for a new or copied preset that is not taken yet: "base", "base 2", "base 3"... */
fun uniqueName(base: String, taken: Collection<String>, maxLength: Int): String {
    val lower = taken.map { it.lowercase() }.toSet()
    return generateSequence(1) { it + 1 }
        .map { if (it == 1) base else "$base $it" }
        .map { it.take(maxLength) }
        .first { it.lowercase() !in lower }
}
