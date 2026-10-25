package com.github.krpkbhdn.ccmanager.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.claude.hook.StatusLineReport;
import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class SessionUsageServiceTest {

    @Test
    void forgetsUsageOfEndedSessions() {
        var registry = new SessionRegistry(new SessionProperties(Duration.ofMinutes(1)), mock(TranscriptService.class));
        var service = new SessionUsageService(registry);
        var id = registry.register(1L);
        service.on(new StatusLineReport(id, JsonMapper.builder().build().createObjectNode(), Instant.now()));
        assertThat(service.of(id)).isPresent();

        registry.markEnded(id, 0);

        assertThat(service.of(id)).isEmpty();
    }
}
