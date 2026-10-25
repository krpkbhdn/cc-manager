package com.github.krpkbhdn.ccmanager.run.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SetRunConfigurationTool implements McpTool {

    private final RunConfigurationService configurations;

    SetRunConfigurationTool(RunConfigurationService configurations) {
        this.configurations = configurations;
    }

    @Override
    public String name() {
        return "set_run_configuration";
    }

    @Override
    public String description() {
        return "Creates or replaces (by name) a run configuration in cc-manager so the user can start the app from its Run button. "
                + "The command runs in bash (Git Bash on Windows) in the given directory; long-running servers are fine. "
                + "Only register it: the user starts it, do not start the app yourself.";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                "name":{"type":"string","description":"Unique name within the project, e.g. 'my-app (feature-x)'"},
                "command":{"type":"string","description":"Shell command that starts the app, e.g. './gradlew run'"},
                "directory":{"type":"string","description":"Absolute path of the worktree or project folder to run in"}},
                "required":["name","command","directory"]}""";
    }

    @Override
    public String call(long projectId, JsonNode arguments) {
        var saved = configurations.save(projectId, McpTool.requiredText(arguments, "name"), McpTool.requiredText(arguments, "command"),
                Path.of(McpTool.requiredText(arguments, "directory")));
        return "Run configuration '%s' saved: `%s` in %s. The user can start it from cc-manager.".formatted(saved.name(), saved.command(), saved.directory());
    }
}
