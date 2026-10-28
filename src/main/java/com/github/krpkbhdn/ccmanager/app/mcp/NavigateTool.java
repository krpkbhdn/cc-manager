package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class NavigateTool extends AssistantTool {

    private static final Set<String> ROUTES = Set.of("overview", "sessions", "assistant", "teams", "prompts");

    private final AppControlRegistry control;

    NavigateTool(AppControlRegistry control) {
        this.control = control;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "navigate";
    }

    @Override
    public String description() {
        return "Switches the cc-manager UI to one of its top-level screens.";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                "route":{"type":"string","enum":["overview","sessions","assistant","teams","prompts"],"description":"Screen to show"}},
                "required":["route"]}""";
    }

    @Override
    public String call(JsonNode arguments) {
        var route = McpToolDescriptor.requiredText(arguments, "route");
        if (!ROUTES.contains(route)) {
            throw new IllegalArgumentException("Unknown route '" + route + "'; use one of overview, sessions, assistant, teams, prompts");
        }
        control.require().navigate(route);
        return "Showing " + route + ".";
    }
}
