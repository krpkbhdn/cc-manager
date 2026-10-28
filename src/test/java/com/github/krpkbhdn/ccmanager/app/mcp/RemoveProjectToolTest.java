package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RemoveProjectToolTest {

    private final ProjectService projects = mock(ProjectService.class);
    private final SessionRegistry sessions = mock(SessionRegistry.class);
    private final RemoveProjectTool tool = new RemoveProjectTool(projects, sessions);

    RemoveProjectToolTest() {
        when(projects.find(7)).thenReturn(Optional.of(new Project(7, "demo", Path.of("demo"), Instant.EPOCH)));
    }

    @Test
    void refusesWhileSessionsAreLive() {
        var live = mock(SessionSnapshot.class);
        when(live.status()).thenReturn(AgentStatus.IDLE);
        when(sessions.sessions(7)).thenReturn(List.of(live));

        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"project\":7}")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("live session");
        verify(projects, never()).remove(7);
    }

    @Test
    void removesAProjectWithoutLiveSessions() {
        when(sessions.sessions(7)).thenReturn(List.of());

        tool.call(JsonMapper.shared().readTree("{\"project\":7}"));

        verify(projects).remove(7);
    }
}
