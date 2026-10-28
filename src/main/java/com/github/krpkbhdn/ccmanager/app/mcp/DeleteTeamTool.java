package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.team.TeamService;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class DeleteTeamTool extends AssistantTool {

    private final TeamService teams;

    DeleteTeamTool(TeamService teams) {
        this.teams = teams;
    }

    @Override
    public String name() {
        return "delete_team";
    }

    @Override
    public String description() {
        return "Deletes an agent team and clears it as the default team of projects.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"id\":{\"type\":\"integer\",\"description\":\"Team id from list_teams\"}},\"required\":[\"id\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "id");
        var team = teams.find(id).orElseThrow(() -> new IllegalArgumentException("Team not found: " + id));
        teams.delete(id);
        return "Team '%s' deleted.".formatted(team.name());
    }
}
