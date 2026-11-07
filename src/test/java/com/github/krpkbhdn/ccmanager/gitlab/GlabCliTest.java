package com.github.krpkbhdn.ccmanager.gitlab;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.github.GhAvailability;
import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GlabCliTest {

    static final GlabProperties PROPERTIES = new Binder(new MapConfigurationPropertySource()).bindOrCreate("cc-manager.gitlab", GlabProperties.class);
    private static final String HOST = "gitlab.com";

    private static GlabCli failing(String stderr) {
        return new GlabCli(PROPERTIES, (command, dir, timeout) -> new CommandRunner.Result(1, "", stderr, false));
    }

    @Test
    void missingWhenProcessCannotStart() {
        var glab = new GlabCli(PROPERTIES, (command, dir, timeout) -> {
            throw new IOException("no such file");
        });
        assertThat(glab.availability(Path.of("."), HOST)).isEqualTo(GhAvailability.MISSING);
    }

    @Test
    void notAuthenticatedWhenNoLogin() {
        assertThat(failing("No token found. Run glab auth login").availability(Path.of("."), HOST)).isEqualTo(GhAvailability.NOT_AUTHENTICATED);
        assertThat(failing("x not logged in").availability(Path.of("."), HOST)).isEqualTo(GhAvailability.NOT_AUTHENTICATED);
    }

    @Test
    void rejectedTokenIsNotAuthenticatedEvenThoughGlabCallsItAFailedApiCall() {
        var stderr = "x gitlab.com: API call failed: GET https://gitlab.com/api/v4/user: 401 {message: 401 Unauthorized}\n  - No token found";
        assertThat(failing(stderr).availability(Path.of("."), HOST)).isEqualTo(GhAvailability.NOT_AUTHENTICATED);
    }

    @Test
    void failedApiCallToAnUnresolvableHostIsUnknown() {
        var stderr = "x gitlab.example: API call failed: Get \"https://gitlab.example/api/v4/user\": dial tcp: lookup gitlab.example: no such host";
        assertThat(failing(stderr).availability(Path.of("."), HOST)).isEqualTo(GhAvailability.UNKNOWN);
    }

    @Test
    void unreachableHostIsUnknownEvenWhenTheTextMentionsLogin() {
        assertThat(failing("API call failed: dial tcp: lookup gitlab.example: no such host. glab auth login").availability(Path.of("."), HOST))
                .isEqualTo(GhAvailability.UNKNOWN);
        var timedOut = new GlabCli(PROPERTIES, (command, dir, timeout) -> new CommandRunner.Result(-1, "", "", true));
        assertThat(timedOut.availability(Path.of("."), HOST)).isEqualTo(GhAvailability.UNKNOWN);
    }

    @Test
    void readyRunsAuthStatusForTheHostAndIsCachedPerHost() {
        var commands = new ArrayList<List<String>>();
        var now = new long[1];
        var glab = new GlabCli(PROPERTIES, (command, dir, timeout) -> {
            commands.add(command);
            return new CommandRunner.Result(0, "", "", false);
        }, false, () -> now[0]);
        assertThat(glab.availability(Path.of("."), HOST)).isEqualTo(GhAvailability.READY);
        assertThat(commands).containsExactly(List.of("glab", "auth", "status", "--hostname", HOST));
        now[0] += PROPERTIES.readyTtl().toNanos() / 2;
        glab.availability(Path.of("."), HOST);
        assertThat(commands).hasSize(1);
        glab.availability(Path.of("."), "gitlab.other");
        assertThat(commands).hasSize(2);
        now[0] += PROPERTIES.readyTtl().toNanos();
        glab.availability(Path.of("."), HOST);
        assertThat(commands).hasSize(3);
    }

    @Test
    void windowsQuotesEmbeddedQuotesSoOneArgumentStaysOne() throws IOException {
        var commands = new ArrayList<List<String>>();
        var glab = new GlabCli(PROPERTIES, (command, dir, timeout) -> {
            commands.add(command);
            return new CommandRunner.Result(0, "", "", false);
        }, true, System::nanoTime);
        glab.run(Path.of("."), "--title=x\" --target-branch \"evil");
        assertThat(commands.getFirst()).containsExactly("glab", "\"--title=x\\\" --target-branch \\\"evil\"");
    }
}
