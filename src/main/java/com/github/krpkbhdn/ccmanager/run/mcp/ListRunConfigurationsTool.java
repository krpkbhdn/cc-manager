package com.github.krpkbhdn.ccmanager.run.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import com.github.krpkbhdn.ccmanager.run.ActiveRun;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import com.github.krpkbhdn.ccmanager.run.RunService;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListRunConfigurationsTool implements McpTool {

    private final RunConfigurationService configurations;
    private final RunService runs;

    ListRunConfigurationsTool(RunConfigurationService configurations, RunService runs) {
        this.configurations = configurations;
        this.runs = runs;
    }

    @Override
    public String name() {
        return "list_run_configurations";
    }

    @Override
    public String description() {
        return "Lists the run configurations of this project in cc-manager: name, command, directory and whether it is running now. "
                + "Call it before set_run_configuration to find an existing entry of the same worktree.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String call(long projectId, JsonNode arguments) {
        var list = configurations.list(projectId);
        if (list.isEmpty()) {
            return "No run configurations in this project.";
        }
        return list.stream().map(config -> "- name: %s | command: %s | directory: %s | running: %s".formatted(config.name(), config.command(),
                config.directory(), runs.current(config.id()).map(ActiveRun::running).orElse(false) ? "yes" : "no"))
                .collect(Collectors.joining("\n"));
    }
}
