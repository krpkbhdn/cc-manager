package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class AssistantAutoApprovalTest {

    private static final List<String> AUTO_APPROVED = List.of(
            "add_project", "get_help", "get_mcp_server", "get_plugin", "get_project", "get_prompt", "get_skill", "git_branches",
            "git_diff", "git_fetch", "git_log", "git_status", "git_worktrees", "list_mcp_servers", "list_plugins", "list_projects",
            "list_prompts", "list_runs", "list_sessions", "list_skills", "list_teams", "navigate", "open_project", "queue_prompt",
            "resume_session", "set_group", "set_prompt", "start_run", "start_session", "stop_run");

    private static final List<String> NEEDS_PERMISSION = List.of(
            "delete_prompt", "delete_skill", "delete_team", "set_skill", "set_team", "install_plugin", "uninstall_plugin", "set_plugin_enabled",
            "add_marketplace", "remove_marketplace", "add_mcp_server", "remove_mcp_server", "remove_project", "git_commit",
            "git_push", "git_pull", "git_checkout", "git_add_worktree", "git_remove_worktree");

    @Test
    void onlyReadOnlyAndEasilyUndoneToolsAreAutoApproved() {
        var endpoint = new AssistantMcpEndpoint(AssistantTools.all(), JsonMapper.builder().build());

        assertThat(endpoint.autoApprovedToolNames()).containsExactlyElementsOf(AUTO_APPROVED);
    }

    @Test
    void writesDeletesAndConfigurationToolsAskPermission() {
        var endpoint = new AssistantMcpEndpoint(AssistantTools.all(), JsonMapper.builder().build());

        assertThat(endpoint.autoApprovedToolNames()).doesNotContainAnyElementsOf(NEEDS_PERMISSION);
        assertThat(AssistantTools.all().stream().map(AssistantMcpTool::name)).containsAll(NEEDS_PERMISSION);
    }
}
