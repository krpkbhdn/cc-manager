package com.github.krpkbhdn.ccmanager.claude.mcp;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The project MCP server {@code cc-manager} that cc-manager hands to every project session it launches; it knows only {@link McpTool}s. */
@Component
public class McpEndpoint extends McpJsonRpcServer<McpTool> {

    static final String SERVER_NAME = "cc-manager";
    static final String INSTRUCTIONS = "This server belongs to Claude Code Manager, the desktop app running this session. "
            + "Use its run configuration tools so the user can start the app from cc-manager. "
            + "See the cc-manager:run-configurations skill for when to use them.";

    private final SessionRegistry sessions;

    public McpEndpoint(List<McpTool> tools, SessionRegistry sessions, JsonMapper mapper) {
        super(tools, mapper, SERVER_NAME, INSTRUCTIONS);
        this.sessions = sessions;
    }

    /** @return the JSON response, empty for a notification */
    public Optional<String> handle(String ccmSessionId, byte[] body) {
        return respond(body, (tool, arguments) -> {
            var session = sessions.find(ccmSessionId)
                    .orElseThrow(() -> new IllegalStateException("This session is not managed by cc-manager anymore"));
            return tool.call(session.projectId(), arguments);
        });
    }
}
