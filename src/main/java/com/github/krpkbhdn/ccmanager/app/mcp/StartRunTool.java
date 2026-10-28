package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.run.RunService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class StartRunTool implements AssistantMcpTool {

    private final RunService runs;

    StartRunTool(RunService runs) {
        this.runs = runs;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "start_run";
    }

    @Override
    public String description() {
        return "Starts a run configuration; its command runs in a console tab of the project.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"configurationId\":{\"type\":\"integer\",\"description\":\"Id from list_runs\"}},\"required\":[\"configurationId\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "configurationId");
        runs.start(id);
        return "Run configuration %s started.".formatted(id);
    }
}
