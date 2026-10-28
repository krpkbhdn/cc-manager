package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListProjectsTool extends AssistantTool {

    private final ProjectService projects;

    ListProjectsTool(ProjectService projects) {
        this.projects = projects;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_projects";
    }

    @Override
    public String description() {
        return "Lists the projects registered in cc-manager: id, name and path. Use the id as the 'project' argument of other tools.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String call(JsonNode arguments) {
        return json(projects.list().stream()
                .map(p -> Map.of("id", p.id(), "name", p.name(), "path", p.path().toString()))
                .toList());
    }
}
