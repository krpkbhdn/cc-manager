package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListSessionsTool implements AssistantMcpTool {

    private final SessionRegistry sessions;

    ListSessionsTool(SessionRegistry sessions) {
        this.sessions = sessions;
    }

    @Override
    public String name() {
        return "list_sessions";
    }

    @Override
    public String description() {
        return "Lists the Claude sessions with id, project id, status (IDLE, WORKING, NEEDS_ACTION, OFFLINE) and the last activity; optionally only those of one project.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"project\":{\"type\":\"integer\",\"description\":\"Project id, omit for all\"}}}";
    }

    @Override
    public String call(JsonNode arguments) {
        var list = arguments.hasNonNull("project") ? sessions.sessions(ToolArguments.requiredId(arguments, "project")) : sessions.sessions();
        if (list.isEmpty()) {
            return "No sessions.";
        }
        return list.stream().map(session -> "- id: %s | project: %s | status: %s | activity: %s".formatted(session.ccmSessionId(), session.projectId(),
                session.status(), session.activity() == null ? "-" : session.activity())).collect(Collectors.joining("\n"));
    }
}
