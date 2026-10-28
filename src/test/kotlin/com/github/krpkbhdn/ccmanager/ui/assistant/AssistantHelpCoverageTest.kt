package com.github.krpkbhdn.ccmanager.ui.assistant

import com.github.krpkbhdn.ccmanager.ui.shortcuts.AppShortcut
import com.github.krpkbhdn.ccmanager.ui.state.Route
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Drift guard: the bundled help must name every screen and every shortcut, and every tool needs a real group. */
class AssistantHelpCoverageTest {

    private val help = Files.readString(Path.of("src/main/resources/assistant/help.md"))

    private val routeWords = mapOf(
        "Overview" to "Overview", "Teams" to "Teams", "Prompts" to "Prompt Library", "Plugins" to "Plugins",
        "Mcp" to "MCP servers", "Skills" to "Skills", "Sessions" to "All sessions", "Assistant" to "Assistant", "Project" to "project",
    )

    @Test
    fun everyRouteIsMentioned() {
        val routes = Route::class.java.permittedSubclasses.map { it.simpleName }

        assertThat(routes).isNotEmpty().allSatisfy { assertThat(routeWords).containsKey(it) }
        routes.forEach { assertThat(help).contains(routeWords.getValue(it)) }
    }

    @Test
    fun everyShortcutIsMentioned() {
        AppShortcut.entries.filter { (it.projectSlot ?: 1) == 1 }.forEach {
            val spelled = it.hint(false).replace(",", "comma").replace("`", "backtick")
            assertThat(help).`as`(it.name).contains(spelled)
        }
    }

    @Test
    fun everyToolMatchesAGroupRule() {
        val tools = toolNames().map { ToolSummary(it, "d") }

        assertThat(groupTools(tools).filter { it.title == "Other" }).isEmpty()
    }

    private fun toolNames(): List<String> =
        Files.walk(Path.of("src/main/java/com/github/krpkbhdn/ccmanager/app/mcp")).use { paths ->
            paths.map { Files.readString(it.takeIf(Files::isRegularFile) ?: return@map "") }.toList()
        }.flatMap { Regex("""public String name\(\) \{\s*return "([a-z_]+)";""").findAll(it).map { m -> m.groupValues[1] }.toList() }
}
