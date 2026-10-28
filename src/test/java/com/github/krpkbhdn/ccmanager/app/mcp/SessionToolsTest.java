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
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import tools.jackson.databind.json.JsonMapper;

class SessionToolsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final ClaudeLauncher launcher = mock(ClaudeLauncher.class);
    @SuppressWarnings("unchecked")
    private final ObjectProvider<ClaudeLauncher> launcherProvider = mock(ObjectProvider.class);
    private final TeamService teams = mock(TeamService.class);
    private final AppControl control = mock(AppControl.class);
    private final AppControlRegistry app = new AppControlRegistry();

    @BeforeEach
    void resolveLauncher() {
        when(launcherProvider.getObject()).thenReturn(launcher);
    }

    private static SessionSnapshot snapshot(String id, AgentStatus status) {
        return new SessionSnapshot(id, 3, null, status, "Reading", null, null, null, null, null, List.of(), 0, null);
    }

    @Test
    void listShowsIdProjectStatusAndActivity() {
        when(sessions.sessions(3)).thenReturn(List.of(snapshot("s1", AgentStatus.WORKING)));

        var text = new ListSessionsTool(sessions).call(mapper.createObjectNode().put("project", 3));

        assertThat(text).contains("id: s1 | project: 3 | status: WORKING | activity: Reading");
    }

    @Test
    void startWithPromptLaunchesTaskAndAttachesIt() {
        app.connect(control);
        var launched = new LaunchedSession("s9", 3, "claude", null);
        when(launcher.launchTask(3, "fix it")).thenReturn(launched);

        new StartSessionTool(launcherProvider, teams, app).call(mapper.createObjectNode().put("project", 3).put("prompt", "fix it"));

        verify(control).attachSession(launched);
    }

    @Test
    void startWithoutPromptLaunchesNewSession() {
        app.connect(control);
        var launched = new LaunchedSession("s9", 3, "claude", null);
        when(launcher.launch(3, LaunchMode.NEW)).thenReturn(launched);

        new StartSessionTool(launcherProvider, teams, app).call(mapper.createObjectNode().put("project", 3));

        verify(control).attachSession(launched);
    }

    @Test
    void startAppliesTheProjectDefaultTeam() {
        app.connect(control);
        var team = new Team(5, "Dev", List.of());
        var launched = new LaunchedSession("s9", 3, "claude", null);
        when(teams.projectDefaults()).thenReturn(Map.of(3L, 5L));
        when(teams.find(5)).thenReturn(Optional.of(team));
        when(launcher.launchTeam(3, team, "fix it")).thenReturn(launched);

        new StartSessionTool(launcherProvider, teams, app).call(mapper.createObjectNode().put("project", 3).put("prompt", "fix it"));

        verify(control).attachSession(launched);
    }

    @Test
    void startFailsWhenUiNotReady() {
        assertThatThrownBy(() -> new StartSessionTool(launcherProvider, teams, app).call(mapper.createObjectNode().put("project", 3)))
                .hasMessage("UI not ready");
    }

    @Test
    void queueTargetsTheLiveSessionOfTheProject() {
        app.connect(control);
        when(sessions.sessions(3)).thenReturn(List.of(snapshot("old", AgentStatus.OFFLINE), snapshot("live", AgentStatus.IDLE)));

        new QueuePromptTool(sessions, app).call(mapper.createObjectNode().put("project", 3).put("text", "go"));

        verify(control).queuePrompt("live", "go");
    }

    @Test
    void queueWithoutLiveSessionSaysToStartOne() {
        app.connect(control);
        when(sessions.sessions(3)).thenReturn(List.of(snapshot("old", AgentStatus.OFFLINE)));

        assertThatThrownBy(() -> new QueuePromptTool(sessions, app).call(mapper.createObjectNode().put("project", 3).put("text", "go")))
                .hasMessageContaining("use start_session");
    }

    @Test
    void queueFailsWhenUiNotReady() {
        assertThatThrownBy(() -> new QueuePromptTool(sessions, app).call(mapper.createObjectNode().put("project", 3).put("text", "go")))
                .hasMessage("UI not ready");
    }
}
