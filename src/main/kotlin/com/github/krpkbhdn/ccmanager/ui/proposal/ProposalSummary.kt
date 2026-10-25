package com.github.krpkbhdn.ccmanager.ui.proposal

import com.github.krpkbhdn.ccmanager.claude.proposal.FormProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PermissionProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.PlanProposal
import com.github.krpkbhdn.ccmanager.claude.proposal.Proposal
import com.github.krpkbhdn.ccmanager.claude.proposal.QuestionsProposal
import tools.jackson.databind.JsonNode

private const val SNIPPET_LIMIT = 400
private const val HEADLINE_LIMIT = 140
private const val MCP_PREFIX = "mcp__"
private const val MCP_SEPARATOR = "__"
private val FILE_TOOLS = setOf("Read", "Write", "Edit", "MultiEdit", "NotebookEdit")

/** One labelled piece of a tool call as the user should read it; [mono] for code, paths and JSON. */
data class SummaryBlock(val label: String?, val text: String, val mono: Boolean)

/** The readable parts of a tool call: the command, file, address or, for anything unknown, the input as JSON. */
fun permissionSummary(toolName: String, input: JsonNode): List<SummaryBlock> = when {
    toolName == "Bash" -> listOfNotNull(
        SummaryBlock(null, input.text("command"), true),
        input.text("description").takeIf { it.isNotBlank() }?.let { SummaryBlock("Description", it, false) },
    )
    toolName in FILE_TOOLS -> fileBlocks(toolName, input)
    toolName == "WebFetch" -> listOf(SummaryBlock("URL", input.text("url"), true))
    toolName.startsWith(MCP_PREFIX) -> listOf(SummaryBlock("MCP tool", mcpLabel(toolName), false), jsonBlock(input))
    else -> listOf(jsonBlock(input))
}

private fun fileBlocks(toolName: String, input: JsonNode): List<SummaryBlock> {
    val path = input.text("file_path").ifEmpty { input.text("notebook_path") }
    val blocks = mutableListOf(SummaryBlock("File", path, true))
    if (toolName == "Edit") {
        val old = input.text("old_string")
        val new = input.text("new_string")
        if (old.length <= SNIPPET_LIMIT && new.length <= SNIPPET_LIMIT) {
            blocks += SummaryBlock("Replace", old, true)
            blocks += SummaryBlock("With", new, true)
        }
    }
    if (toolName == "MultiEdit") blocks += SummaryBlock(null, "${input.path("edits").size()} edits", false)
    return blocks
}

private fun mcpLabel(toolName: String): String {
    val parts = toolName.removePrefix(MCP_PREFIX).split(MCP_SEPARATOR, limit = 2)
    return if (parts.size == 2) "${parts[0]} / ${parts[1]}" else toolName
}

private fun jsonBlock(input: JsonNode) = SummaryBlock("Input", input.toPrettyString(), true)

private fun JsonNode.text(field: String): String = path(field).asString("")

/** One line for the overview: what the session waits for. */
fun proposalHeadline(proposal: Proposal): String = when (proposal) {
    is QuestionsProposal -> proposal.questions().first().text()
    is PlanProposal -> "Plan ready for approval"
    is PermissionProposal -> "Allow ${proposal.toolName()}: ${permissionSummary(proposal.toolName(), proposal.toolInput()).first().text}"
    is FormProposal -> proposal.message().ifBlank { "${proposal.serverName()} asks for input" }
}.lineSequence().first().take(HEADLINE_LIMIT)
