package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallation;
import com.github.krpkbhdn.ccmanager.claude.install.ClaudeInstallationService;
import com.github.krpkbhdn.ccmanager.claude.install.LoginState;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** The process runner is a static utility, so a small script stands in for the claude executable; the 10 s timeout is not tested. */
class BackgroundAgentServiceTest {

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");

    @TempDir
    Path temp;

    private BackgroundAgentService serviceWith(ClaudeInstallation claude) {
        var installation = mock(ClaudeInstallationService.class);
        when(installation.current()).thenReturn(claude);
        return new BackgroundAgentService(installation);
    }

    private static ClaudeInstallation installed(Path executable) {
        return new ClaudeInstallation(true, executable, "1", LoginState.LOGGED_IN, null);
    }

    private Path script(String stdout, int exitCode) throws IOException {
        var out = temp.resolve("out.txt");
        Files.writeString(out, stdout);
        var script = temp.resolve(WINDOWS ? "claude.cmd" : "claude.sh");
        Files.writeString(script, WINDOWS
                ? "@echo off\r\ntype \"" + out + "\"\r\nexit /b " + exitCode + "\r\n"
                : "#!/bin/sh\ncat \"" + out + "\"\nexit " + exitCode + "\n");
        script.toFile().setExecutable(true);
        return script;
    }

    @Test
    void notInstalledGivesNoAgents() {
        var claude = new ClaudeInstallation(false, null, null, LoginState.UNKNOWN, "missing");

        assertThat(serviceWith(claude).list(temp)).isEmpty();
    }

    @Test
    void installedWithoutExecutableGivesNoAgents() {
        assertThat(serviceWith(installed(null)).list(temp)).isEmpty();
    }

    @Test
    void executableThatCannotBeStartedGivesNoAgentsInsteadOfThrowing() {
        assertThat(serviceWith(installed(temp.resolve("no-such-claude"))).list(temp)).isEmpty();
    }

    @Test
    void parsesTheJsonPrintedByTheCli() throws IOException {
        var json = "[{\"id\":\"a1\",\"name\":\"build\",\"state\":\"running\",\"summary\":\"s\"},{\"name\":\"interactive\"}]";

        var agents = serviceWith(installed(script(json, 0))).list(temp);

        assertThat(agents).containsExactly(new BackgroundAgent("a1", "build", "running", "s"));
    }

    @Test
    void cmdShimWithMetacharacterInTheFolderIsNotRunAndGivesNoAgents() throws IOException {
        assumeTrue(WINDOWS);
        var json = "[{\"id\":\"a1\",\"name\":\"build\",\"state\":\"running\",\"summary\":\"s\"}]";
        var folder = Files.createDirectory(temp.resolve("100%done"));

        assertThat(serviceWith(installed(script(json, 0))).list(folder)).isEmpty();
    }

    @Test
    void emptyOutputGivesNoAgents() throws IOException {
        assertThat(serviceWith(installed(script("", 0))).list(temp)).isEmpty();
    }

    @Test
    void malformedOutputGivesNoAgents() throws IOException {
        assertThat(serviceWith(installed(script("not json at all", 0))).list(temp)).isEmpty();
    }
}
