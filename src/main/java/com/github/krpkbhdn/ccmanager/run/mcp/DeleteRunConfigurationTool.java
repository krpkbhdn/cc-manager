package com.github.krpkbhdn.ccmanager.run.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class DeleteRunConfigurationTool implements McpTool {

    private final RunConfigurationService configurations;

    DeleteRunConfigurationTool(RunConfigurationService configurations) {
        this.configurations = configurations;
    }

    @Override
    public String name() {
        return "delete_run_configuration";
    }

    @Override
    public String description() {
        return "Deletes a run configuration of this project by name, e.g. when its worktree is merged or removed.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\",\"description\":\"Name of the configuration\"}},\"required\":[\"name\"]}";
    }

    @Override
    public String call(long projectId, JsonNode arguments) {
        var name = McpTool.requiredText(arguments, "name");
        return configurations.delete(projectId, name) ? "Run configuration '" + name + "' deleted." : "No run configuration named '" + name + "' existed.";
    }
}
