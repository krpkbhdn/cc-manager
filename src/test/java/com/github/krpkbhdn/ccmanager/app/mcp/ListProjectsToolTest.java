package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class ListProjectsToolTest {

    @Test
    void listsIdNameAndPath() {
        var projects = mock(ProjectService.class);
        when(projects.list()).thenReturn(List.of(new Project(7, "demo", Path.of("demo"), Instant.EPOCH)));

        var result = new ListProjectsTool(projects).call(JsonMapper.shared().createObjectNode());

        assertThat(result).contains("\"id\":7").contains("\"name\":\"demo\"").contains("demo");
    }

    @Test
    void isOnlyForTheAssistant() {
        assertThat(new ListProjectsTool(mock(ProjectService.class))).isInstanceOf(AssistantMcpTool.class).isNotInstanceOf(McpTool.class);
    }
}
