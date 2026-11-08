package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown.Category
import com.github.krpkbhdn.ccmanager.usage.context.ContextBreakdown.Item
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextHintsTest {

    private fun breakdown(
        categories: List<Category> = emptyList(),
        memory: List<Item> = emptyList(),
        mcp: List<Item> = emptyList(),
        agents: List<Item> = emptyList(),
        skills: List<Item> = emptyList(),
    ) = ContextBreakdown("m", 0, 200_000, categories, memory, mcp, agents, skills, false, Instant.EPOCH, listOf(), "")

    @Test
    fun `small context needs no advice`() {
        assertThat(contextHints(breakdown(memory = listOf(Item("/a/CLAUDE.md", "Project", 500, false))))).isEmpty()
    }

    @Test
    fun `heavy memory names the heaviest file`() {
        val hints = contextHints(breakdown(memory = listOf(Item("C:\\w\\CLAUDE.md", "Project", 7_000, false), Item("/h/CLAUDE.md", "User", 1_000, false))))

        assertThat(hints).hasSize(1)
        assertThat(hints.single()).contains("8k").contains("CLAUDE.md (7k)")
    }

    @Test
    fun `mcp tools are summed per server and only heavy servers are named`() {
        val tools = listOf(Item("a1", "big", 3_000, false), Item("a2", "big", 3_000, false), Item("b1", "small", 900, false))

        val hints = contextHints(breakdown(mcp = tools))

        assertThat(hints).hasSize(1)
        assertThat(hints.single()).contains("big").contains("6k")
    }

    @Test
    fun `large messages suggest compact`() {
        val hints = contextHints(breakdown(categories = listOf(Category("Messages", 90_000, 45.0))))

        assertThat(hints.single()).contains("/compact")
    }

    @Test
    fun `heavy deferred mcp tools are reported`() {
        val hints = contextHints(breakdown(categories = listOf(Category("MCP tools (deferred)", 12_000, 6.0))))

        assertThat(hints.single()).contains("12k").contains("Disable MCP servers")
    }

    @Test
    fun `messages over half the window suggest compact even when small in tokens`() {
        val hints = contextHints(breakdown(categories = listOf(Category("Messages", 30_000, 50.0))))

        assertThat(hints.single()).contains("/compact")
    }

    @Test
    fun `heavy mcp server hint offers disabling or project scope`() {
        val hints = contextHints(breakdown(mcp = listOf(Item("a", "big", 6_000, false))))

        assertThat(hints.single()).contains("project scope")
    }

    @Test
    fun `heavy skills and agents are reported`() {
        val hints = contextHints(breakdown(skills = listOf(Item("graphify", "User", 5_000, false)), agents = listOf(Item("reviewer", "Project", 2_500, false))))

        assertThat(hints).hasSize(2)
        assertThat(hints.first()).contains("graphify")
    }
}
