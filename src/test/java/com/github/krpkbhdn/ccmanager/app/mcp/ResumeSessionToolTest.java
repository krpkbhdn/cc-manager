package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.app.AppControl;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

class ResumeSessionToolTest {

    private final ClaudeLauncher launcher = mock(ClaudeLauncher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ClaudeLauncher> provider = mock(ObjectProvider.class);
    private final ProjectService projects = mock(ProjectService.class);
    private final TeamService teams = mock(TeamService.class);
    private final AppControl control = mock(AppControl.class);
    private final AppControlRegistry app = new AppControlRegistry();
    private final ResumeSessionTool tool = new ResumeSessionTool(provider, projects, teams, app);

    private String call() {
        return tool.call(JsonMapper.shared().readTree("{\"project\":3}"));
    }

    @Test
    void continuesWithTheDefaultTeamAndAttaches() {
        app.connect(control);
        var team = new Team(5, "Dev", List.of());
        var launched = new LaunchedSession("s9", 3, "claude", null);
        when(provider.getObject()).thenReturn(launcher);
        when(projects.find(3)).thenReturn(Optional.of(new Project(3, "demo", Path.of("demo"), Instant.EPOCH)));
        when(teams.projectDefaults()).thenReturn(Map.of(3L, 5L));
        when(teams.find(5)).thenReturn(Optional.of(team));
        when(launcher.launch(3, LaunchMode.CONTINUE, team)).thenReturn(launched);

        assertThat(call()).contains("s9");

        verify(control).attachSession(launched);
    }

    @Test
    void failsWhenUiNotReady() {
        assertThatThrownBy(this::call).hasMessage("UI not ready");
    }
}
