package com.github.krpkbhdn.ccmanager.usage;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.hook.StatusLineReport;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RateLimitServiceTest {

    private static final Instant T0 = Instant.ofEpochSecond(1_000_000);
    private final JsonMapper mapper = JsonMapper.builder().build();
    private final RateLimitService service = new RateLimitService();

    private StatusLineReport report(int percent, Instant receivedAt) {
        var json = "{\"rate_limits\":{\"five_hour\":{\"used_percentage\":" + percent + ",\"resets_at\":2000000}}}";
        return new StatusLineReport("s", mapper.readTree(json), receivedAt);
    }

    @Test
    void startsEmpty() {
        assertThat(service.current()).isEmpty();
    }

    @Test
    void newestReportWinsAndNotifiesListeners() {
        var notified = new AtomicInteger();
        service.subscribe(notified::incrementAndGet);

        service.on(report(10, T0));
        service.on(report(20, T0.plusSeconds(5)));
        service.on(report(5, T0.minusSeconds(5)));

        assertThat(service.current().orElseThrow().fiveHour().usedPercent()).isEqualTo(20);
        assertThat(notified).hasValue(2);
    }

    @Test
    void reportWithoutRateLimitsKeepsTheLastSnapshot() {
        service.on(report(10, T0));

        service.on(new StatusLineReport("s", mapper.readTree("{}"), T0.plusSeconds(5)));

        assertThat(service.current().orElseThrow().fiveHour().usedPercent()).isEqualTo(10);
    }
}
