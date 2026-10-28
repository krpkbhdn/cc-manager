package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.app.AppControl;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class OpenProjectToolTest {

    private final ProjectService projects = mock(ProjectService.class);
    private final AppControl ui = mock(AppControl.class);
    private final AppControlRegistry registry = new AppControlRegistry();
    private final OpenProjectTool tool = new OpenProjectTool(projects, registry);

    OpenProjectToolTest() {
        registry.connect(ui);
        when(projects.find(7)).thenReturn(Optional.of(new Project(7, "demo", Path.of("demo"), Instant.EPOCH)));
    }

    @Test
    void opensOnTheRequestedTab() {
        tool.call(JsonMapper.shared().readTree("{\"project\":7,\"tab\":\"git\"}"));

        verify(ui).openProject(7, "git");
    }

    @Test
    void rejectsUnknownTab() {
        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"project\":7,\"tab\":\"x\"}")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("Unknown tab");
    }
}
