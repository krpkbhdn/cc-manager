package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.run.ActiveRun;
import com.github.krpkbhdn.ccmanager.run.RunConfiguration;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import com.github.krpkbhdn.ccmanager.run.RunService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RunToolsTest {

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RunConfigurationService configurations = mock(RunConfigurationService.class);
    private final RunService runs = mock(RunService.class);

    @Test
    void listShowsIdAndLatestRunState() {
        when(configurations.list(3)).thenReturn(List.of(new RunConfiguration(1, 3, "app", "run", Path.of("d")), new RunConfiguration(2, 3, "b", "x", Path.of("d"))));
        when(runs.current(1)).thenReturn(Optional.of(new ActiveRun(1, "r", "l", null, OptionalInt.of(2))));
        when(runs.current(2)).thenReturn(Optional.empty());

        var text = new ListRunsTool(configurations, runs).call(mapper.createObjectNode().put("project", 3));

        assertThat(text).contains("id: 1 | name: app").contains("exited with code 2").contains("id: 2 | name: b").contains("never run");
    }

    @Test
    void startAndStopDelegateToRunService() {
        new StartRunTool(runs).call(mapper.createObjectNode().put("configurationId", 5));
        new StopRunTool(runs).call(mapper.createObjectNode().put("configurationId", 5));

        verify(runs).start(5);
        verify(runs).stop(5);
    }

    @Test
    void nonNumericIdIsRejected() {
        assertThatThrownBy(() -> new StartRunTool(runs).call(mapper.createObjectNode().put("configurationId", "abc")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
