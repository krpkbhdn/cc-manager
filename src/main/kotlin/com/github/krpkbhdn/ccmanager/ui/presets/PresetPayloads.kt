package com.github.krpkbhdn.ccmanager.ui.presets

import tools.jackson.core.util.DefaultIndenter
import tools.jackson.core.util.DefaultPrettyPrinter
import tools.jackson.core.util.Separators
import tools.jackson.databind.JsonNode
import tools.jackson.databind.json.JsonMapper
import tools.jackson.databind.node.ObjectNode

const val SETTINGS_NAME = "settings"
const val SKILL_FILE = "SKILL.md"

private val mapper = JsonMapper.builder().build()
private val folderName = Regex("[A-Za-z0-9][A-Za-z0-9._-]*")

/** Builds the payload JSON of each component kind; the backend validates and normalises it again. */
object PresetPayloads {
    fun instruction(markdown: String): ObjectNode = mapper.createObjectNode().put("markdown", markdown)

    fun agent(content: String): ObjectNode = mapper.createObjectNode().put("content", content)

    fun marketplace(source: String): ObjectNode = mapper.createObjectNode().put("source", source)

    fun plugin(): ObjectNode = mapper.createObjectNode()

    fun skill(files: Map<String, String>): ObjectNode {
        val node = mapper.createObjectNode()
        val inner = node.putObject("files")
        files.forEach { (path, text) -> inner.put(path, text) }
        return node
    }

    fun file(path: String, content: String, gitignore: List<String>): ObjectNode {
        val node = mapper.createObjectNode().put("path", path).put("content", content)
        val lines = node.putArray("gitignore")
        gitignore.forEach { lines.add(it) }
        return node
    }

    fun settings(fragment: JsonNode): ObjectNode = mapper.createObjectNode().set("fragment", fragment)

    fun mcp(configJson: String): ObjectNode = mapper.createObjectNode().set("config", mapper.readTree(configJson))
}

fun JsonNode.text(field: String): String = path(field).asString("")

fun JsonNode.skillFiles(): Map<String, String> = path("files").properties().associate { it.key to it.value.asString("") }

fun JsonNode.gitignoreLines(): List<String> = path("gitignore").toList().map { it.asString("") }

fun prettyJson(node: JsonNode): String = mapper.writer().with(prettyPrinter()).writeValueAsString(node)

private fun prettyPrinter() = DefaultPrettyPrinter(Separators.createDefaultInstance().withObjectNameValueSpacing(Separators.Spacing.AFTER)).also {
    val indent = DefaultIndenter("  ", "\n")
    it.indentObjectsWith(indent)
    it.indentArraysWith(indent)
}

/** The text of the gitignore box turned into lines: trimmed, blanks dropped. */
fun parseGitignore(text: String): List<String> = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

/** Null when [text] is a JSON object; otherwise what is wrong, for an inline error. */
fun settingsProblem(text: String): String? = when {
    text.isBlank() -> "Enter the settings as a JSON object"
    else -> try {
        if (mapper.readTree(text).isObject) null else "The settings must be a JSON object, like { \"permissions\": {} }"
    } catch (e: RuntimeException) {
        "That is not valid JSON"
    }
}

fun parseSettings(text: String): JsonNode = mapper.readTree(text)

/** Same rule as the backend for a path in the project: relative, no drive, no `..`. */
fun pathProblem(path: String): String? {
    val slashed = path.trim().replace('\\', '/')
    return when {
        slashed.isEmpty() -> "Enter the path of the file"
        slashed.startsWith("/") || slashed.contains(":") -> "The path must be relative to the project"
        slashed.split('/').any { it == ".." } -> "The path must stay inside the project"
        slashed.split('/').all { it.isEmpty() || it == "." } -> "The path must name a file"
        else -> null
    }
}

/** Same rule as the backend for skill and agent names, which become folder or file names. */
fun folderNameProblem(name: String): String? = when {
    name.isBlank() -> "Give it a name"
    !folderName.matches(name.trim()) || name.contains("..") -> "Use letters, digits, '.', '_' or '-'"
    else -> null
}
