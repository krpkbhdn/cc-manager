package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.util.List;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base of the Assistant's project and navigation tools: JSON results and the numeric {@code project} argument. */
abstract class AssistantTool implements AssistantMcpTool {

    static final String PROJECT_PROPERTY = "\"project\":{\"type\":\"integer\",\"description\":\"Project id from list_projects\"}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    static Project requiredProject(ProjectService projects, JsonNode arguments) {
        var id = arguments.path("project");
        if (!id.canConvertToLong()) {
            throw new IllegalArgumentException("Argument 'project' must be a numeric project id (see list_projects)");
        }
        return projects.find(id.asLong()).orElseThrow(() -> new IllegalArgumentException("Project not found: " + id.asLong()));
    }

    static List<SessionSnapshot> liveSessions(SessionRegistry sessions, long projectId) {
        return sessions.sessions(projectId).stream().filter(s -> s.status() != AgentStatus.OFFLINE).toList();
    }
}
