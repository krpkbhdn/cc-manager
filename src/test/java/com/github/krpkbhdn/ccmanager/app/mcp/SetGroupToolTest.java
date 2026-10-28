package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SetGroupToolTest {

    private final ProjectGroupService groups = mock(ProjectGroupService.class);
    private final SetGroupTool tool = new SetGroupTool(groups);
    private final Project group = new Project(9, "Stack", Path.of("g"), Instant.EPOCH, ModelSelection.DEFAULT, true, null);

    @Test
    void createsWithoutId() {
        when(groups.create("Stack", List.of(1L, 2L))).thenReturn(group);

        assertThat(tool.call(JsonMapper.shared().readTree("{\"name\":\"Stack\",\"members\":[1,2]}"))).contains("id 9").contains("2 member");
    }

    @Test
    void replacesMembersWithId() {
        when(groups.setMembers(9, List.of(1L))).thenReturn(group);

        tool.call(JsonMapper.shared().readTree("{\"id\":9,\"members\":[1]}"));

        verify(groups).setMembers(9, List.of(1L));
    }

    @Test
    void rejectsNonNumericMembers() {
        assertThatThrownBy(() -> tool.call(JsonMapper.shared().readTree("{\"name\":\"x\",\"members\":[\"a\"]}")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
