package com.github.krpkbhdn.ccmanager.claude.install;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.workspace.FakeHostEnvironment;
import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import com.github.krpkbhdn.ccmanager.workspace.SystemHostEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ClaudeInstallationServiceTest {

    private static final boolean WINDOWS = new SystemHostEnvironment().windows();

    @TempDir
    Path temp;

    @Test
    void parsesVersionFromCliOutput() {
        assertThat(ClaudeInstallationService.parseVersion("2.1.211 (Claude Code)\n")).contains("2.1.211");
        assertThat(ClaudeInstallationService.parseVersion("garbage")).isEmpty();
        assertThat(ClaudeInstallationService.parseVersion(null)).isEmpty();
    }

    @Test
    void notFoundProblemNamesInstallCommandPerOs() {
        assertThat(ClaudeInstallationService.notFoundProblem(true)).contains("install.ps1");
        assertThat(ClaudeInstallationService.notFoundProblem(false)).contains("install.sh");
    }

    @Test
    void candidatesPutPathBeforeNativeInstallerAndFallbacks() {
        var home = Path.of("/home/u");
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, home).withPath(Path.of("/a"), Path.of("/b"));

        assertThat(new ClaudeBinaryLocator().candidates(host)).containsExactly(
                Path.of("/a/claude"), Path.of("/b/claude"), home.resolve(".local/bin/claude"),
                Path.of("/usr/local/bin/claude"), Path.of("/opt/homebrew/bin/claude"));
    }

    @Test
    void windowsCandidatesTryEveryExeBeforeAnyCmdShim() {
        var home = Path.of("C:\\Users\\u");
        var host = new FakeHostEnvironment(OperatingSystem.WINDOWS, home).withPath(Path.of("C:\\tools"));

        assertThat(new ClaudeBinaryLocator().candidates(host)).containsExactly(
                Path.of("C:\\tools\\claude.exe"), home.resolve(".local").resolve("bin").resolve("claude.exe"),
                Path.of("C:\\tools\\claude.cmd"), home.resolve(".local").resolve("bin").resolve("claude.cmd"),
                Path.of("C:\\tools\\claude.bat"), home.resolve(".local").resolve("bin").resolve("claude.bat"));
    }

    @Test
    void reportsMissingBinaryWithInstallHint() {
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, temp);

        var result = new ClaudeInstallationService(host, new ClaudeBinaryLocator()).recheck();

        assertThat(result.installed()).isFalse();
        assertThat(result.problem()).contains("curl -fsSL");
    }

    @Test
    void detectsVersionAndLoggedInStateFromFakeExecutable() throws IOException {
        var result = detectWithFakeClaude(0);

        assertThat(result.installed()).isTrue();
        assertThat(result.version()).isEqualTo("9.8.7");
        assertThat(result.login()).isEqualTo(LoginState.LOGGED_IN);
    }

    @Test
    void mapsAuthExitCodeOneToLoggedOutAndOthersToUnknown() throws IOException {
        assertThat(detectWithFakeClaude(1).login()).isEqualTo(LoginState.LOGGED_OUT);
        assertThat(detectWithFakeClaude(7).login()).isEqualTo(LoginState.UNKNOWN);
    }

    @Test
    void detectsRealClaudeOnThisMachineWhenPresent() {
        var service = new ClaudeInstallationService(new SystemHostEnvironment(), new ClaudeBinaryLocator());
        var result = service.recheck();
        assumeTrue(result.installed(), "claude is not installed here");

        System.out.println("Real claude: " + result);
        assertThat(result.version()).matches("\\d+\\.\\d+\\.\\d+");
    }

    private ClaudeInstallation detectWithFakeClaude(int authExit) throws IOException {
        var dir = Files.createTempDirectory(temp, "bin");
        var script = dir.resolve(WINDOWS ? "claude.cmd" : "claude");
        Files.writeString(script, WINDOWS ? windowsScript(authExit) : unixScript(authExit));
        assertThat(script.toFile().setExecutable(true)).isTrue();
        var host = new FakeHostEnvironmentWithRealFiles(dir);
        return new ClaudeInstallationService(host, new ClaudeBinaryLocator()).recheck();
    }

    private static String windowsScript(int authExit) {
        return "@echo off\r\nif \"%1\"==\"--version\" (echo 9.8.7 (Claude Code) & exit /b 0)\r\nexit /b " + authExit + "\r\n";
    }

    private static String unixScript(int authExit) {
        return "#!/bin/sh\nif [ \"$1\" = \"--version\" ]; then echo '9.8.7 (Claude Code)'; exit 0; fi\nexit " + authExit + "\n";
    }

    /** Real file checks over a single PATH directory, so the fake script is found like a real install. */
    private static final class FakeHostEnvironmentWithRealFiles extends SystemHostEnvironment {
        private final Path directory;

        FakeHostEnvironmentWithRealFiles(Path directory) {
            this.directory = directory;
        }

        @Override
        public List<Path> pathDirectories() {
            return List.of(directory);
        }

        @Override
        public Path home() {
            return directory;
        }
    }
}
