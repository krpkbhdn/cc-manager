package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.Set;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class OpenProjectTool extends AssistantTool {

    private static final Set<String> TABS = Set.of("sessions", "git", "run");

    private final ProjectService projects;
    private final AppControlRegistry control;

    OpenProjectTool(ProjectService projects, AppControlRegistry control) {
        this.projects = projects;
        this.control = control;
    }

    @Override
    public String name() {
        return "open_project";
    }

    @Override
    public String description() {
        return "Shows a project in the cc-manager UI, optionally on a given tab.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY
                + ",\"tab\":{\"type\":\"string\",\"enum\":[\"sessions\",\"git\",\"run\"],\"description\":\"Tab to show (default: the project's current tab)\"}},"
                + "\"required\":[\"project\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var project = requiredProject(projects, arguments);
        var tab = arguments.path("tab").asString("").strip();
        if (!tab.isEmpty() && !TABS.contains(tab)) {
            throw new IllegalArgumentException("Unknown tab '" + tab + "'; use one of sessions, git, run");
        }
        control.require().openProject(project.id(), tab.isEmpty() ? null : tab);
        return "Opened project '%s'%s.".formatted(project.name(), tab.isEmpty() ? "" : " on tab " + tab);
    }
}
