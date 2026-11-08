package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.extension.mcp.McpHealth;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpService;
import com.github.krpkbhdn.ccmanager.extension.mcp.McpStatus;
import com.github.krpkbhdn.ccmanager.preset.PresetComponent;
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class McpServerApplierTest {

    private static final Path DIR = Path.of("project");

    private final McpService mcp = mock(McpService.class);
    private final McpServerApplier applier = new McpServerApplier(mcp);

    private static PresetComponent server(long id, String name) {
        return new PresetComponent(id, PresetComponentKind.MCP_SERVER, name, JsonMapper.builder().build().readTree("{\"config\":{\"command\":\"x\"}}"));
    }

    private List<ComponentOutcome> applyAll(PresetComponent... components) throws Exception {
        var outcomes = new java.util.ArrayList<ComponentOutcome>();
        for (var component : components) {
            outcomes.add(applier.apply(component, DIR));
        }
        return applier.afterApply(outcomes, DIR);
    }

    @Test
    void applyNeverRunsTheHealthCheck() throws Exception {
        var outcome = applier.apply(server(1, "a"), DIR);

        assertThat(outcome.status()).isEqualTo(OutcomeStatus.APPLIED);
        verify(mcp, never()).health(any());
    }

    @Test
    void failingHealthCheckKeepsServersApplied() throws Exception {
        when(mcp.health(DIR)).thenThrow(new IllegalStateException("timeout"));

        assertThat(applyAll(server(1, "a"))).extracting(ComponentOutcome::status).containsExactly(OutcomeStatus.APPLIED);
    }

    @Test
    void healthIsCheckedOnceForAllServersAndSignInIsMappedByName() throws Exception {
        when(mcp.health(DIR)).thenReturn(List.of(new McpHealth("b", "", McpStatus.NEEDS_AUTHENTICATION, ""), new McpHealth("a", "", McpStatus.CONNECTED, "")));

        var outcomes = applyAll(server(1, "a"), server(2, "b"), server(3, "c"));

        assertThat(outcomes).extracting(ComponentOutcome::status)
                .containsExactly(OutcomeStatus.APPLIED, OutcomeStatus.SIGN_IN_NEEDED, OutcomeStatus.APPLIED);
        verify(mcp, times(1)).health(DIR);
    }
}
