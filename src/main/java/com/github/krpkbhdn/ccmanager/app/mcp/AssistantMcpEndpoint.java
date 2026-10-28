package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.launch.AssistantToolPolicy;
import com.github.krpkbhdn.ccmanager.claude.mcp.McpJsonRpcServer;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** The separate MCP server {@code cc-manager-app} of the Assistant session; it knows only {@link AssistantMcpTool}s and never runs project tools. */
@Component
public class AssistantMcpEndpoint extends McpJsonRpcServer<AssistantMcpTool> implements AssistantToolPolicy {

    public static final String SERVER_NAME = "cc-manager-app";
    static final String INSTRUCTIONS = "You control Claude Code Manager through these tools. "
            + "Resolve project names with list_projects. Act directly instead of asking the user to do it.";

    private final List<String> autoApprovedToolNames;

    public AssistantMcpEndpoint(List<AssistantMcpTool> tools, JsonMapper mapper) {
        super(tools, mapper, SERVER_NAME, INSTRUCTIONS);
        this.autoApprovedToolNames = tools.stream().filter(AssistantMcpTool::autoApproved).map(AssistantMcpTool::name).sorted().toList();
    }

    @Override
    public List<String> autoApprovedToolNames() {
        return autoApprovedToolNames;
    }

    /** @return the JSON response, empty for a notification */
    public Optional<String> handle(byte[] body) {
        return respond(body, (tool, arguments) -> tool.call(arguments));
    }
}
