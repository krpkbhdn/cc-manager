package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SetTeamTool extends AssistantTool {

    private final TeamService teams;

    SetTeamTool(TeamService teams) {
        this.teams = teams;
    }

    @Override
    public String name() {
        return "set_team";
    }

    @Override
    public String description() {
        return "Creates an agent team, or with 'id' replaces the name and the whole role list of an existing one; exactly one role is the lead.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"Team id from list_teams, omit to create\"},"
                + "\"name\":{\"type\":\"string\"},"
                + "\"roles\":{\"type\":\"array\",\"description\":\"The complete role list\",\"items\":{\"type\":\"object\",\"properties\":{"
                + "\"name\":{\"type\":\"string\",\"description\":\"Lowercase letters, digits and dashes\"},"
                + "\"description\":{\"type\":\"string\",\"description\":\"When the lead should delegate to this role\"},"
                + "\"prompt\":{\"type\":\"string\",\"description\":\"The role's system prompt\"},"
                + "\"model\":{\"type\":\"string\"},\"effort\":{\"type\":\"string\"},"
                + "\"tools\":{\"type\":\"string\",\"description\":\"Comma list of tool names or mcp__server__*, omit to inherit all\"},"
                + "\"lead\":{\"type\":\"boolean\"}},\"required\":[\"name\",\"description\",\"prompt\"]}}},"
                + "\"required\":[\"name\",\"roles\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var name = McpToolDescriptor.requiredText(arguments, "name");
        var roles = roles(arguments.path("roles"));
        var id = optionalId(arguments, "id");
        var team = id == null ? teams.create(name, roles) : teams.update(id, name, roles);
        return "Team '%s' (id %d) saved with %d role(s).".formatted(team.name(), team.id(), team.roles().size());
    }

    private static List<RoleDraft> roles(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Argument 'roles' must be an array of role objects");
        }
        var roles = new ArrayList<RoleDraft>();
        node.forEach(role -> roles.add(new RoleDraft(text(role, "name"), text(role, "description"), text(role, "prompt"),
                text(role, "model"), text(role, "effort"), text(role, "tools"), role.path("lead").asBoolean(false))));
        return roles;
    }

    private static String text(JsonNode role, String field) {
        return role.hasNonNull(field) ? role.path(field).asString() : null;
    }
}
