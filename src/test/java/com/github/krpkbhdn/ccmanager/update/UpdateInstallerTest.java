package com.github.krpkbhdn.ccmanager.update;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class UpdateInstallerTest {

    @Test
    void commandQuotesEveryPathEvenWithoutSpaces() {
        var command = UpdateInstaller.command(Path.of("C:\\t\\u.cmd"), Path.of("C:\\a&b\\app.msi"), "C:\\x^y\\app.exe");

        assertThat(command).containsExactly("cmd.exe", "/c", "start", "\"\"", "/min", "\"C:\\t\\u.cmd\"", "\"C:\\a&b\\app.msi\"", "\"C:\\x^y\\app.exe\"");
    }

    @Test
    void scriptWaitsForTheProcessThenInstallsAndRestarts() {
        var script = UpdateInstaller.script(4242);

        assertThat(script).contains("PID eq 4242").contains("find \"4242\"").contains("msiexec /i \"%~1\"").contains("start \"\" \"%~2\"").contains("del \"%~f0\"");
    }
}
