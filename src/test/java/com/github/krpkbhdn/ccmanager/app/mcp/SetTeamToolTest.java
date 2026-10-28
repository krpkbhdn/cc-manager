package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class SetTeamToolTest {

    private static final String ROLES = "\"roles\":[{\"name\":\"lead\",\"description\":\"Leads\",\"prompt\":\"Go\",\"lead\":true}]";

    private final TeamService teams = mock(TeamService.class);
    private final SetTeamTool tool = new SetTeamTool(teams);

    @Test
    @SuppressWarnings("unchecked")
    void createsWithoutIdFromRoleObjects() {
        when(teams.create(eq("Dev"), anyList())).thenReturn(new Team(5, "Dev", List.of()));

        assertThat(tool.call(JsonMapper.shared().readTree("{\"name\":\"Dev\"," + ROLES + "}"))).contains("id 5");

        var captor = ArgumentCaptor.forClass(List.class);
        verify(teams).create(eq("Dev"), captor.capture());
        assertThat(((RoleDraft) captor.getValue().get(0)).lead()).isTrue();
    }

    @Test
    void updatesWithId() {
        when(teams.update(eq(5L), eq("Dev"), anyList())).thenReturn(new Team(5, "Dev", List.of()));

        tool.call(JsonMapper.shared().readTree("{\"id\":5,\"name\":\"Dev\"," + ROLES + "}"));

        verify(teams).update(eq(5L), eq("Dev"), anyList());
    }

    @Test
    void reportsInvalidRoleName() {
        var arguments = JsonMapper.shared().readTree("{\"name\":\"Dev\",\"roles\":[{\"name\":\"Bad Name\",\"description\":\"d\",\"prompt\":\"p\"}]}");

        assertThatThrownBy(() -> tool.call(arguments)).isInstanceOf(IllegalArgumentException.class);
    }
}
