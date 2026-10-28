package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class DeleteTeamToolTest {

    private final TeamService teams = mock(TeamService.class);
    private final DeleteTeamTool tool = new DeleteTeamTool(teams);

    @Test
    void deletesTheTeam() {
        when(teams.find(5)).thenReturn(Optional.of(new Team(5, "Dev", List.of())));

        assertThat(tool.call(JsonMapper.shared().readTree("{\"id\":5}"))).contains("Dev");

        verify(teams).delete(5);
    }

    @Test
    void rejectsUnknownTeamAndAsksForApproval() {
        when(teams.find(6)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"id\":6}"))).hasMessageContaining("6");
        assertThat(tool.autoApproved()).isFalse();
    }
}
