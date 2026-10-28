package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.run.RunService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class StopRunTool implements AssistantMcpTool {

    private final RunService runs;

    StopRunTool(RunService runs) {
        this.runs = runs;
    }

    @Override
    public String name() {
        return "stop_run";
    }

    @Override
    public String description() {
        return "Stops the running process of a run configuration.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"configurationId\":{\"type\":\"integer\",\"description\":\"Id from list_runs\"}},\"required\":[\"configurationId\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "configurationId");
        runs.stop(id);
        return "Run configuration %s stopped.".formatted(id);
    }
}
