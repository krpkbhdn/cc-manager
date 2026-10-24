package com.github.krpkbhdn.ccmanager.usage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class RateLimitParserTest {

    private static final Instant NOW = Instant.ofEpochSecond(1_000_000);
    private final JsonMapper mapper = JsonMapper.builder().build();

    private Optional<RateLimitSnapshot> parse(String json) {
        return RateLimitParser.parse(mapper.readTree(json), NOW);
    }

    @Test
    void readsBothWindows() {
        var snapshot = parse("{\"rate_limits\":{\"five_hour\":{\"used_percentage\":23.5,\"resets_at\":1003600},"
                + "\"seven_day\":{\"used_percentage\":41,\"resets_at\":1086400}}}").orElseThrow();

        assertThat(snapshot.fiveHour()).isEqualTo(new RateLimitWindow(23.5, Instant.ofEpochSecond(1_003_600)));
        assertThat(snapshot.sevenDay()).isEqualTo(new RateLimitWindow(41, Instant.ofEpochSecond(1_086_400)));
        assertThat(snapshot.receivedAt()).isEqualTo(NOW);
    }

    @Test
    void keepsTheWindowThatIsPresent() {
        var snapshot = parse("{\"rate_limits\":{\"seven_day\":{\"used_percentage\":5,\"resets_at\":1086400}}}").orElseThrow();

        assertThat(snapshot.fiveHour()).isNull();
        assertThat(snapshot.sevenDay().usedPercent()).isEqualTo(5);
    }

    @Test
    void noRateLimitsFieldYieldsNothing() {
        assertThat(parse("{\"model\":{}}")).isEmpty();
    }

    @Test
    void malformedWindowsAreSkipped() {
        assertThat(parse("{\"rate_limits\":{\"five_hour\":{\"used_percentage\":\"x\",\"resets_at\":1003600},\"seven_day\":{}}}")).isEmpty();
    }

    @Test
    void windowThatAlreadyResetIsIgnored() {
        var snapshot = parse("{\"rate_limits\":{\"five_hour\":{\"used_percentage\":90,\"resets_at\":999999},"
                + "\"seven_day\":{\"used_percentage\":5,\"resets_at\":1086400}}}").orElseThrow();

        assertThat(snapshot.fiveHour()).isNull();
        assertThat(snapshot.sevenDay()).isNotNull();
    }
}
