package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class AddProjectTool extends AssistantTool {

    private final ProjectService projects;
    private final AppControlRegistry control;

    AddProjectTool(ProjectService projects, AppControlRegistry control) {
        this.projects = projects;
        this.control = control;
    }

    @Override
    public String name() {
        return "add_project";
    }

    @Override
    public String description() {
        return "Registers an existing directory as a project in cc-manager. Optionally opens it in the UI.";
    }

    @Override
    public String inputSchema() {
        return """
                {"type":"object","properties":{
                "path":{"type":"string","description":"Absolute path of an existing directory"},
                "open":{"type":"boolean","description":"Open the project in the UI after adding (default false)"}},
                "required":["path"]}""";
    }

    @Override
    public String call(JsonNode arguments) {
        var project = projects.add(Path.of(McpToolDescriptor.requiredText(arguments, "path")));
        if (arguments.path("open").asBoolean(false)) {
            control.require().openProject(project.id(), null);
        }
        return json(Map.of("id", project.id(), "name", project.name(), "path", project.path().toString()));
    }
}
