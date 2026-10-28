package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class QueuePromptTool implements AssistantMcpTool {

    private final SessionRegistry sessions;
    private final AppControlRegistry app;

    QueuePromptTool(SessionRegistry sessions, AppControlRegistry app) {
        this.sessions = sessions;
        this.app = app;
    }

    @Override
    public String name() {
        return "queue_prompt";
    }

    @Override
    public String description() {
        return "Queues a prompt for the live session of a project; it is typed when the session is idle.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"project\":{\"type\":\"integer\",\"description\":\"Project id\"},"
                + "\"text\":{\"type\":\"string\"}},\"required\":[\"project\",\"text\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var control = app.require();
        var project = ToolArguments.requiredId(arguments, "project");
        var text = McpToolDescriptor.requiredText(arguments, "text");
        var session = sessions.sessions(project).stream().filter(candidate -> candidate.status() != AgentStatus.OFFLINE).findFirst()
                .orElseThrow(() -> new IllegalStateException("No live session in project " + project + ", use start_session"));
        control.queuePrompt(session.ccmSessionId(), text);
        return "Prompt queued for session " + session.ccmSessionId() + ".";
    }
}
