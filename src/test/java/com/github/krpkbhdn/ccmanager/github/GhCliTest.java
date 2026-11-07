package com.github.krpkbhdn.ccmanager.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.CommandRunner;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

class GhCliTest {

    static final GhProperties PROPERTIES = new Binder(new MapConfigurationPropertySource()).bindOrCreate("cc-manager.github", GhProperties.class);

    @Test
    void missingWhenProcessCannotStart() {
        var gh = new GhCli(PROPERTIES, (command, dir, timeout) -> {
            throw new IOException("no such file");
        });
        assertThat(gh.availability(Path.of("."))).isEqualTo(GhAvailability.MISSING);
    }

    @Test
    void notAuthenticatedWhenAuthStatusFails() {
        var gh = new GhCli(PROPERTIES, (command, dir, timeout) -> new CommandRunner.Result(1, "", "not logged in", false));
        assertThat(gh.availability(Path.of("."))).isEqualTo(GhAvailability.NOT_AUTHENTICATED);
    }

    @Test
    void readyWhenAuthStatusSucceeds() {
        var commands = new ArrayList<List<String>>();
        var gh = new GhCli(PROPERTIES, (command, dir, timeout) -> {
            commands.add(command);
            return new CommandRunner.Result(0, "", "", false);
        });
        assertThat(gh.availability(Path.of("."))).isEqualTo(GhAvailability.READY);
        assertThat(commands).containsExactly(List.of("gh", "auth", "status"));
    }

    @Test
    void offlineOrTimedOutAuthStatusIsUnknownNotSignedOut() {
        var offline = new GhCli(PROPERTIES, (command, dir, timeout) -> new CommandRunner.Result(1, "", "error connecting to api.github.com", false));
        var timedOut = new GhCli(PROPERTIES, (command, dir, timeout) -> new CommandRunner.Result(-1, "", "", true));
        assertThat(offline.availability(Path.of("."))).isEqualTo(GhAvailability.UNKNOWN);
        assertThat(timedOut.availability(Path.of("."))).isEqualTo(GhAvailability.UNKNOWN);
    }

    @Test
    void readyIsCachedUntilTheTtlPasses() {
        var calls = new int[1];
        var now = new long[1];
        var gh = new GhCli(PROPERTIES, (command, dir, timeout) -> {
            calls[0]++;
            return new CommandRunner.Result(0, "", "", false);
        }, false, () -> now[0]);
        gh.availability(Path.of("."));
        now[0] += PROPERTIES.readyTtl().toNanos() / 2;
        gh.availability(Path.of("."));
        assertThat(calls[0]).isEqualTo(1);
        now[0] += PROPERTIES.readyTtl().toNanos();
        gh.availability(Path.of("."));
        assertThat(calls[0]).isEqualTo(2);
    }

    @Test
    void windowsQuotesEmbeddedQuotesSoOneArgumentStaysOne() throws IOException {
        var commands = new ArrayList<List<String>>();
        var gh = new GhCli(PROPERTIES, (command, dir, timeout) -> {
            commands.add(command);
            return new CommandRunner.Result(0, "", "", false);
        }, true, System::nanoTime);
        gh.run(Path.of("."), "--title=x\" --base \"evil");
        assertThat(commands.getFirst()).containsExactly("gh", "\"--title=x\\\" --base \\\"evil\"");
    }
}
