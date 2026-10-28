package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpToolDescriptor;
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class SetGroupTool extends AssistantTool {

    private final ProjectGroupService groups;

    SetGroupTool(ProjectGroupService groups) {
        this.groups = groups;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "set_group";
    }

    @Override
    public String description() {
        return "Creates a project group, or with 'id' replaces the members of an existing one; members are ungrouped non-group projects.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"Group id to update, omit to create\"},"
                + "\"name\":{\"type\":\"string\",\"description\":\"Group name, required when creating\"},"
                + "\"members\":{\"type\":\"array\",\"items\":{\"type\":\"integer\"},\"description\":\"Project ids from list_projects\"}},"
                + "\"required\":[\"members\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var members = memberIds(arguments.path("members"));
        var id = optionalId(arguments, "id");
        var group = id == null ? groups.create(McpToolDescriptor.requiredText(arguments, "name"), members) : groups.setMembers(id, members);
        return "Group '%s' (id %d) has %d member(s).".formatted(group.name(), group.id(), members.size());
    }

    private static List<Long> memberIds(JsonNode node) {
        if (!node.isArray()) {
            throw new IllegalArgumentException("Argument 'members' must be an array of project ids");
        }
        var ids = new ArrayList<Long>();
        node.forEach(element -> {
            if (!element.isIntegralNumber()) {
                throw new IllegalArgumentException("Argument 'members' must contain numeric project ids (see list_projects)");
            }
            ids.add(element.asLong());
        });
        return ids;
    }
}
