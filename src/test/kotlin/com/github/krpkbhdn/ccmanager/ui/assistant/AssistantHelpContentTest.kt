package com.github.krpkbhdn.ccmanager.ui.assistant

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AssistantHelpContentTest {

    private fun tool(name: String) = ToolSummary(name, "d")

    @Test
    fun groupsToolsByNameInRuleOrder() {
        val groups = groupTools(listOf("navigate", "stop_run", "git_status", "add_project", "queue_prompt", "list_runs", "mystery", "set_group", "list_teams", "get_prompt", "install_plugin", "add_mcp_server", "get_help").map(::tool))

        assertThat(groups.map { it.title }).containsExactly("Git", "Runs", "Sessions", "Projects", "Groups", "Teams", "Prompts", "Skills & plugins", "MCP servers", "UI", "Help", "Other")
        assertThat(groups.first { it.title == "Runs" }.tools.map { it.name }).containsExactly("list_runs", "stop_run")
    }

    @Test
    fun dropsEmptyGroups() {
        assertThat(groupTools(listOf(tool("git_push"))).map { it.title }).containsExactly("Git")
    }

    @Test
    fun oneLineKeepsTheFirstSentence() {
        assertThat(oneLine("Lists projects. Returns ids.\nMore.")).isEqualTo("Lists projects")
    }

    @Test
    fun hotkeysUseThePlatformChord() {
        assertThat(helpHotkeys(false)).contains(HotkeyLine("Assistant", "Ctrl+Shift+I"))
        assertThat(helpHotkeys(true).first { it.label == "Assistant" }.keys).isNotEqualTo("Ctrl+Shift+I")
    }
}
