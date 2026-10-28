package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.git.GitStatus;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.run.RunConfiguration;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class GetProjectToolTest {

    private final ProjectService projects = mock(ProjectService.class);
    private final GitStatusService git = mock(GitStatusService.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final RunConfigurationService runs = mock(RunConfigurationService.class);
    private final GetProjectTool tool = new GetProjectTool(projects, git, sessions, runs);

    @Test
    void reportsBranchLiveSessionsAndRunConfigurations() {
        var path = Path.of("demo");
        when(projects.find(7)).thenReturn(Optional.of(new Project(7, "demo", path, Instant.EPOCH)));
        when(git.status(path)).thenReturn(new GitStatus(true, "main", "abc", false, null, 0, 0, GitStatus.Operation.NONE, 0, null, List.of()));
        var live = mock(SessionSnapshot.class);
        when(live.ccmSessionId()).thenReturn("s1");
        when(live.status()).thenReturn(AgentStatus.WORKING);
        var offline = mock(SessionSnapshot.class);
        when(offline.status()).thenReturn(AgentStatus.OFFLINE);
        when(sessions.sessions(7)).thenReturn(List.of(live, offline));
        when(runs.list(7)).thenReturn(List.of(new RunConfiguration(1, 7, "app", "./run", path)));

        var result = tool.call(JsonMapper.shared().readTree("{\"project\":7}"));

        assertThat(result).contains("\"branch\":\"main\"").contains("\"status\":\"WORKING\"").contains("\"name\":\"app\"")
                .doesNotContain("OFFLINE");
    }

    @Test
    void rejectsUnknownProject() {
        when(projects.find(anyLong())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"project\":9}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("9");
    }
}
