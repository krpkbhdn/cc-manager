package com.github.krpkbhdn.ccmanager.run.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.run.ActiveRun;
import com.github.krpkbhdn.ccmanager.run.RunConfiguration;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import com.github.krpkbhdn.ccmanager.run.RunService;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RunConfigurationToolsTest {

    private static final long PROJECT = 3;
    private static final Path DIRECTORY = Path.of("worktree").toAbsolutePath();

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RunConfigurationService configurations = mock(RunConfigurationService.class);
    private final RunService runs = mock(RunService.class);

    @Test
    void listShowsEachConfigurationWithItsRunningState() {
        var active = mock(ActiveRun.class);
        when(active.running()).thenReturn(true);
        when(configurations.list(PROJECT)).thenReturn(List.of(new RunConfiguration(1, PROJECT, "app (x)", "./gradlew run", DIRECTORY),
                new RunConfiguration(2, PROJECT, "other", "npm start", DIRECTORY)));
        when(runs.current(1)).thenReturn(Optional.of(active));
        when(runs.current(2)).thenReturn(Optional.empty());

        var text = new ListRunConfigurationsTool(configurations, runs).call(PROJECT, mapper.createObjectNode());

        assertThat(text).contains("name: app (x) | command: ./gradlew run | directory: " + DIRECTORY + " | running: yes")
                .contains("name: other | command: npm start | directory: " + DIRECTORY + " | running: no");
    }

    @Test
    void listSaysSoWhenThereAreNone() {
        when(configurations.list(PROJECT)).thenReturn(List.of());

        assertThat(new ListRunConfigurationsTool(configurations, runs).call(PROJECT, mapper.createObjectNode())).contains("No run configurations");
    }

    @Test
    void setSavesInTheProjectOfTheSession() {
        when(configurations.save(PROJECT, "app", "./gradlew run", DIRECTORY))
                .thenReturn(new RunConfiguration(1, PROJECT, "app", "./gradlew run", DIRECTORY));
        var arguments = mapper.createObjectNode().put("name", "app").put("command", "./gradlew run").put("directory", DIRECTORY.toString());

        assertThat(new SetRunConfigurationTool(configurations).call(PROJECT, arguments)).contains("'app' saved");
    }

    @Test
    void setRequiresEveryArgument() {
        var arguments = mapper.createObjectNode().put("name", "app").put("command", "x");

        assertThatThrownBy(() -> new SetRunConfigurationTool(configurations).call(PROJECT, arguments))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("directory");
    }

    @Test
    void deleteReportsWhetherItExisted() {
        when(configurations.delete(PROJECT, "app")).thenReturn(true);
        when(configurations.delete(PROJECT, "gone")).thenReturn(false);
        var tool = new DeleteRunConfigurationTool(configurations);

        assertThat(tool.call(PROJECT, mapper.createObjectNode().put("name", "app"))).contains("deleted");
        assertThat(tool.call(PROJECT, mapper.createObjectNode().put("name", "gone"))).contains("existed");
    }
}
