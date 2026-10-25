package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.skill.Skill
import com.github.krpkbhdn.ccmanager.ui.extensions.ExtensionContext

private val kebabCase = Regex("[a-z0-9]+(-[a-z0-9]+)*")
private const val MAX_NAME_LENGTH = 64
private const val FRONTMATTER_FENCE = "---"
private val headingLine = Regex("^(#{1,6})\\s+(.*)$")
private val bulletLine = Regex("^\\s*([-*+]|\\d+[.)])\\s+(.*)$")

/** Same rule as the backend (kebab-case, up to 64 characters); null when the name is fine. */
fun skillNameProblem(name: String, existing: Collection<String>): String? = when {
    name.isBlank() -> "Give the skill a name"
    name.length > MAX_NAME_LENGTH -> "Use at most $MAX_NAME_LENGTH characters"
    !kebabCase.matches(name) -> "Use lowercase letters, digits and single dashes, like code-review"
    name in existing -> "A skill named $name already exists here"
    else -> null
}

fun skillDescriptionProblem(description: String): String? = if (description.isBlank()) "Say when Claude should use the skill" else null

/** The scope a new skill is created in: the user's for Global, the project's otherwise (skills have no local scope). */
fun newSkillScope(context: ExtensionContext): ExtensionScope = context.writableScopes.first { it != ExtensionScope.LOCAL }

/** A skill shown in a context; [inherited] ones come from the user and cannot be changed from a project. */
data class SkillEntry(val skill: Skill, val inherited: Boolean) {
    val key: String get() = skill.file.toString()
    val removable: Boolean get() = !inherited && !skill.readOnly()
}

data class SkillGroups(val own: List<SkillEntry>, val inherited: List<SkillEntry>, val plugins: List<SkillEntry>) {
    val size: Int get() = own.size + inherited.size + plugins.size
    val all: List<SkillEntry> get() = own + inherited + plugins
}

/** Own skills, user skills inherited into a project, and read-only plugin skills; each by name, every query word must match. */
fun skillGroups(skills: List<Skill>, context: ExtensionContext, query: String = ""): SkillGroups {
    val words = query.lowercase().split(' ').filter { it.isNotBlank() }
    val matching = skills.filter { skill -> words.all { skill.mentions(it) } }.sortedBy { it.name.lowercase() }
    val plugins = matching.filter { it.readOnly() }.map { SkillEntry(it, inherited = false) }
    val own = matching.filterNot { it.readOnly() }.map { SkillEntry(it, inherited = !context.owns(it.scope, context.projectPath)) }
    return SkillGroups(own.filterNot { it.inherited }, own.filter { it.inherited }, plugins)
}

private fun Skill.mentions(word: String) =
    name.contains(word, ignoreCase = true) || description.contains(word, ignoreCase = true) || pluginId.contains(word, ignoreCase = true)

/** SKILL.md without its front matter, which the detail pane shows as fields. */
fun skillBody(content: String): String {
    val lines = content.lines()
    if (lines.firstOrNull()?.trim() != FRONTMATTER_FENCE) return content.trim()
    val end = lines.drop(1).indexOfFirst { it.trim() == FRONTMATTER_FENCE }
    return if (end < 0) content.trim() else lines.drop(end + 2).joinToString("\n").trim()
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class Item(val marker: String, val text: String) : MarkdownBlock
    data class Code(val text: String) : MarkdownBlock
}

/** Headings, list items, fenced code and paragraphs; inline markup stays as typed. */
fun markdownBlocks(markdown: String): List<MarkdownBlock> {
    val blocks = mutableListOf<MarkdownBlock>()
    val paragraph = mutableListOf<String>()
    val code = mutableListOf<String>()
    var inCode = false
    fun flushParagraph() {
        if (paragraph.isNotEmpty()) blocks += MarkdownBlock.Paragraph(paragraph.joinToString(" ")).also { paragraph.clear() }
    }
    for (line in markdown.lines()) {
        when {
            line.trimStart().startsWith("```") -> {
                flushParagraph()
                if (inCode) blocks += MarkdownBlock.Code(code.joinToString("\n")).also { code.clear() }
                inCode = !inCode
            }
            inCode -> code += line
            line.isBlank() -> flushParagraph()
            headingLine.matches(line) -> { flushParagraph(); headingLine.matchEntire(line)!!.let { blocks += MarkdownBlock.Heading(it.groupValues[1].length, it.groupValues[2].trim()) } }
            bulletLine.matches(line) -> { flushParagraph(); bulletLine.matchEntire(line)!!.let { blocks += MarkdownBlock.Item(if (it.groupValues[1][0].isDigit()) it.groupValues[1] else "•", it.groupValues[2].trim()) } }
            else -> paragraph += line.trim()
        }
    }
    if (inCode && code.isNotEmpty()) blocks += MarkdownBlock.Code(code.joinToString("\n"))
    flushParagraph()
    return blocks
}
