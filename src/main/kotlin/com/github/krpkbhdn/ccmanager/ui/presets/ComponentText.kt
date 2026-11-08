package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.preset.PresetComponent
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import tools.jackson.databind.JsonNode

private const val SECONDARY_LIMIT = 140
private const val MIN_PROSE_LINE = 12
private const val PROSE_LINES = 3
private val frontmatterLine = Regex("^([A-Za-z][\\w-]*):\\s*(.*)$")

/** What a card shows for a component: a title and one muted line under it. */
data class ComponentLine(val title: String, val secondary: String)

fun lineOf(component: PresetComponent): ComponentLine {
    val payload = component.payload()
    val name = component.name()
    return when (component.kind()) {
        PresetComponentKind.INSTRUCTION -> ComponentLine(name, firstTextLine(payload.text("markdown")))
        PresetComponentKind.SKILL -> {
            val files = payload.skillFiles()
            val description = frontmatterField(files[SKILL_FILE].orEmpty(), "description")
            ComponentLine(name, description.ifBlank { fileCount(files.size) })
        }
        PresetComponentKind.PLUGIN -> ComponentLine(name.substringBefore('@'), "From ${name.substringAfter('@', "an unknown marketplace")}")
        PresetComponentKind.MARKETPLACE -> ComponentLine(name, payload.text("source"))
        PresetComponentKind.MCP_SERVER -> ComponentLine(name, mcpSummary(payload.path("config")))
        PresetComponentKind.AGENT -> {
            val content = payload.text("content")
            ComponentLine(name, frontmatterField(content, "description").ifBlank { firstTextLine(content) })
        }
        PresetComponentKind.FILE -> ComponentLine(payload.text("path").ifBlank { name }, fileSummary(payload))
        PresetComponentKind.SETTINGS -> ComponentLine("Project settings", settingsKeys(payload.path("fragment")))
    }
}

private fun fileCount(count: Int) = if (count == 1) "1 file" else "$count files"

private fun fileSummary(payload: JsonNode): String {
    val content = payload.text("content")
    val lines = if (content.isEmpty()) 0 else content.trimEnd('\n').lines().size
    val ignores = payload.gitignoreLines().size
    val parts = listOf(if (lines == 1) "1 line" else "$lines lines") + listOfNotNull("adds $ignores .gitignore ${if (ignores == 1) "line" else "lines"}".takeIf { ignores > 0 })
    return parts.joinToString(SEPARATOR)
}

private fun settingsKeys(fragment: JsonNode): String {
    val keys = fragment.properties().map { it.key }
    return if (keys.isEmpty()) "No keys" else keys.joinToString(", ")
}

/** "stdio · npx -y server" or "http · https://host/mcp"; a config stored as text is shown as it is. */
fun mcpSummary(config: JsonNode): String {
    if (config.isString) return config.asString("").take(SECONDARY_LIMIT)
    val type = config.text("type")
    val target = when {
        config.has("url") -> config.text("url")
        config.has("command") -> (listOf(config.text("command")) + config.path("args").toList().map { it.asString("") }).joinToString(" ")
        else -> config.properties().joinToString(", ") { it.key }
    }
    val kind = type.ifEmpty { if (config.has("url")) "http" else "stdio" }
    return "$kind$SEPARATOR$target".take(SECONDARY_LIMIT)
}

/**
 * The opening prose of markdown: its first few lines joined, skipping blanks, headings, front matter fences and labels
 * (a line shorter than [MIN_PROSE_LINE] or ending with ':'); the first heading when there is no prose at all.
 */
fun firstTextLine(markdown: String): String {
    val lines = markdown.lines().map { it.trim() }
    val prose = lines.filter { it.isNotEmpty() && it != "---" && !it.startsWith("#") && it.length >= MIN_PROSE_LINE && !it.endsWith(":") }
    val text = if (prose.isEmpty()) lines.map { it.trimStart('#').trim() }.firstOrNull { it.isNotEmpty() }.orEmpty() else prose.take(PROSE_LINES).joinToString(" ")
    return text.trimStart('-', '*', '>', ' ').take(SECONDARY_LIMIT)
}

/** The value of a single-line `key: value` in a SKILL.md or agent front matter; empty when there is none. */
fun frontmatterField(content: String, key: String): String {
    val lines = content.removePrefix("﻿").lines()
    if (lines.firstOrNull()?.trim() != "---") return ""
    for (line in lines.drop(1)) {
        if (line.trim() == "---") break
        val match = frontmatterLine.matchEntire(line) ?: continue
        if (match.groupValues[1] == key) return unquote(match.groupValues[2].trim())
    }
    return ""
}

private fun unquote(value: String): String =
    if (value.length >= 2 && (value.startsWith('"') && value.endsWith('"') || value.startsWith('\'') && value.endsWith('\''))) {
        value.substring(1, value.length - 1).replace("\\\"", "\"").replace("\\\\", "\\")
    } else {
        value
    }

/** A SKILL.md with front matter (the description always double-quoted, so any text stays valid YAML) and [body]. */
fun skillMarkdown(name: String, description: String, body: String): String {
    val quoted = description.trim().replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ")
    return listOf("---", "name: $name", "description: \"$quoted\"", "---", "", body.trim(), "").joinToString("\n")
}
