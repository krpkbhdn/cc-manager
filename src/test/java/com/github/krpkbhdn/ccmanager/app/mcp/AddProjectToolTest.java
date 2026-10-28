package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.app.AppControl;
import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.json.JsonMapper;

class AddProjectToolTest {

    private final ProjectService projects = mock(ProjectService.class);
    private final AppControl ui = mock(AppControl.class);
    private final AppControlRegistry registry = new AppControlRegistry();
    private final AddProjectTool tool = new AddProjectTool(projects, registry);

    @TempDir
    Path dir;

    private String args(boolean open) {
        return "{\"path\":\"" + dir.toString().replace("\\", "\\\\") + "\",\"open\":" + open + "}";
    }

    @Test
    void addsAndOpensWhenAsked() {
        registry.connect(ui);
        when(projects.add(dir)).thenReturn(new Project(3, "p", dir, Instant.EPOCH));

        var result = tool.call(JsonMapper.shared().readTree(args(true)));

        assertThat(result).contains("\"id\":3");
        verify(ui).openProject(3, null);
    }

    @Test
    void doesNotTouchTheUiByDefault() {
        registry.connect(ui);
        when(projects.add(dir)).thenReturn(new Project(3, "p", dir, Instant.EPOCH));

        tool.call(JsonMapper.shared().readTree(args(false)));

        verifyNoInteractions(ui);
    }
}
