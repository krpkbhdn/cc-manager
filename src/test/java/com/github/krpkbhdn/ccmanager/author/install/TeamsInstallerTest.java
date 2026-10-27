package com.github.krpkbhdn.ccmanager.author.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.author.AuthorProfileLoader;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeamsInstallerTest {

    final TeamService teams = mock(TeamService.class);
    final AuthorProfileLoader loader = new AuthorProfileLoader();
    final TeamsInstaller installer = new TeamsInstaller(teams, loader);
    final String name = loader.teamTemplates().getFirst().name();

    @Test
    void createsAMissingTeam() {
        when(teams.list()).thenReturn(List.of());

        var outcome = installer.install();

        assertThat(outcome.installed()).containsExactly("team " + name);
        verify(teams).create(any(), anyList());
    }

    @Test
    void replacesTheRolesOfAnExistingTeamWithTheSameName() {
        when(teams.list()).thenReturn(List.of(new Team(7, name, List.of())));

        var outcome = installer.install();

        assertThat(outcome.installed()).containsExactly("team " + name);
        verify(teams).update(anyLong(), anyString(), anyList());
        verify(teams, never()).create(any(), anyList());
    }

    @Test
    void rejectedTeamIsAFailure() {
        when(teams.list()).thenReturn(List.of());
        when(teams.create(any(), anyList())).thenThrow(new IllegalArgumentException("nope"));

        assertThat(installer.install().failed()).containsExactly("team " + name + ": nope");
    }
}
