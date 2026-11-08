package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown

private const val MEMORY_HINT_TOKENS = 4_000L
private const val MCP_SERVER_HINT_TOKENS = 5_000L
private const val SKILLS_HINT_TOKENS = 4_000L
private const val AGENTS_HINT_TOKENS = 2_000L
private const val MESSAGES_HINT_TOKENS = 40_000L
private const val DEFERRED_MCP_HINT_TOKENS = 5_000L
private const val MESSAGES_HINT_PERCENT = 50.0
private const val MESSAGES_CATEGORY = "Messages"
private const val MCP_CATEGORY = "MCP"
const val DEFERRED_MARK = "(deferred)"

/** Short advice for the heavy parts of a measured context, each naming what to trim; empty when nothing stands out. */
fun contextHints(breakdown: ContextBreakdown): List<String> = buildList {
    val memory = breakdown.memoryFiles()
    val memoryTokens = memory.sumOf { it.tokens() }
    if (memoryTokens >= MEMORY_HINT_TOKENS) {
        val heaviest = memory.maxBy { it.tokens() }
        add("Memory files take ${compactTokens(memoryTokens)}; the heaviest is ${heaviest.name().substringAfterLast('/').substringAfterLast('\\')} (${compactTokens(heaviest.tokens())}). Trim CLAUDE.md or move rarely needed parts into skills.")
    }
    mcpServerTokens(breakdown).filter { it.second >= MCP_SERVER_HINT_TOKENS }
        .forEach { (server, tokens) -> add("MCP server $server adds ${compactTokens(tokens)} of tool definitions. Disable it, or move it to project scope, if you do not use it everywhere.") }
    val skills = breakdown.skills()
    if (skills.sumOf { it.tokens() } >= SKILLS_HINT_TOKENS) add("Skills take ${compactTokens(skills.sumOf { it.tokens() })}; the heaviest is ${skills.maxBy { it.tokens() }.name()}. Remove unused plugin skills.")
    val agents = breakdown.agents().sumOf { it.tokens() }
    if (agents >= AGENTS_HINT_TOKENS) add("Custom agents take ${compactTokens(agents)}. Drop agents you do not delegate to.")
    breakdown.categories().filter { it.name().contains(DEFERRED_MARK) && it.name().contains(MCP_CATEGORY) && it.tokens() >= DEFERRED_MCP_HINT_TOKENS }
        .forEach { add("${it.name()} are ${compactTokens(it.tokens())}, loaded only on use. Disable MCP servers you rarely call so they cannot grow the context.") }
    val messages = breakdown.categories().firstOrNull { it.name() == MESSAGES_CATEGORY }
    if (messages != null && (messages.tokens() >= MESSAGES_HINT_TOKENS || messages.percent() >= MESSAGES_HINT_PERCENT)) {
        add("Messages hold ${compactTokens(messages.tokens())}. Run /compact, or start a new session when the topic changes.")
    }
}

/** MCP tool tokens summed per server, heaviest first. */
fun mcpServerTokens(breakdown: ContextBreakdown): List<Pair<String, Long>> =
    breakdown.mcpTools().groupBy { it.group() }.map { (server, tools) -> server to tools.sumOf { it.tokens() } }.sortedByDescending { it.second }
