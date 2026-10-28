package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.team.TeamRole;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListTeamsTool extends AssistantTool {

    private final TeamService teams;

    ListTeamsTool(TeamService teams) {
        this.teams = teams;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_teams";
    }

    @Override
    public String description() {
        return "Lists the agent teams with their roles (name, description, prompt, model, effort, tools, lead) and the project ids that use each as default.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{}}";
    }

    @Override
    public String call(JsonNode arguments) {
        var defaults = teams.projectDefaults();
        return json(teams.list().stream().map(team -> {
            var result = new LinkedHashMap<String, Object>();
            result.put("id", team.id());
            result.put("name", team.name());
            result.put("roles", team.roles().stream().map(ListTeamsTool::role).toList());
            result.put("defaultForProjects", defaults.entrySet().stream().filter(e -> e.getValue() == team.id()).map(Map.Entry::getKey).sorted().toList());
            return result;
        }).toList());
    }

    private static Map<String, Object> role(TeamRole role) {
        var draft = role.draft();
        var result = new LinkedHashMap<String, Object>();
        result.put("name", draft.name());
        result.put("description", draft.description());
        result.put("prompt", draft.prompt());
        result.put("model", draft.model());
        result.put("effort", draft.effort());
        result.put("tools", draft.tools());
        result.put("lead", draft.lead());
        return result;
    }
}
