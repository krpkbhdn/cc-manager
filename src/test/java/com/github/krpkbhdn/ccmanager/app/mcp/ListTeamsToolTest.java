package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamRole;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ListTeamsToolTest {

    @Test
    void showsRolesAndProjectsUsingTheTeam() {
        var teams = mock(TeamService.class);
        var lead = new TeamRole(1, new RoleDraft("lead", "Leads", "Do it", null, null, null, true), 0);
        when(teams.list()).thenReturn(List.of(new Team(5, "Dev", List.of(lead))));
        when(teams.projectDefaults()).thenReturn(Map.of(3L, 5L));

        var text = new ListTeamsTool(teams).call(JsonMapper.shared().createObjectNode());

        assertThat(text).contains("\"name\":\"Dev\"").contains("\"prompt\":\"Do it\"").contains("\"defaultForProjects\":[3]");
    }
}
