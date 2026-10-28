package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class RemoveProjectTool extends AssistantTool {

    private final ProjectService projects;
    private final SessionRegistry sessions;

    RemoveProjectTool(ProjectService projects, SessionRegistry sessions) {
        this.projects = projects;
        this.sessions = sessions;
    }

    @Override
    public String name() {
        return "remove_project";
    }

    @Override
    public String description() {
        return "Unregisters a project from cc-manager (files stay on disk). Refused while the project has live sessions.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var project = requiredProject(projects, arguments);
        var live = liveSessions(sessions, project.id()).size();
        if (live > 0) {
            throw new IllegalStateException("Project '%s' has %d live session(s); close them first".formatted(project.name(), live));
        }
        projects.remove(project.id());
        return "Project '%s' removed.".formatted(project.name());
    }
}
