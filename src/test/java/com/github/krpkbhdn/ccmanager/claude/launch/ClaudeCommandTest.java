package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaudeCommandTest {

    private static final Path EXE = Path.of("claude").toAbsolutePath();
    private static final Path SETTINGS = Path.of("s.json").toAbsolutePath();

    @Test
    void newSessionHasOnlySettings() {
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.NEW))
                .containsExactly(EXE.toString(), "--settings", SETTINGS.toString());
    }

    @Test
    void continueAndResumeAddTheirFlag() {
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.CONTINUE)).endsWith("--continue");
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.RESUME_PICKER)).endsWith("--resume");
    }

    @Test
    void attachAppendsSubcommandAndId() {
        assertThat(ClaudeCommand.attachArguments(EXE, SETTINGS, "ab12")).endsWith("attach", "ab12");
    }

    @Test
    void environmentKeepsParentAndAddsTerminalVariables() {
        Map<String, String> env = ClaudeCommand.environment(Map.of("PATH", "x", "TERM", "dumb"), "ccm-1");

        assertThat(env).containsEntry("PATH", "x")
                .containsEntry("TERM", "xterm-256color")
                .containsEntry("COLORTERM", "truecolor")
                .containsEntry("CCM_SESSION", "ccm-1");
    }

    @Test
    void labelsAreShort() {
        assertThat(ClaudeCommand.label(LaunchMode.NEW)).isEqualTo("claude");
        assertThat(ClaudeCommand.label(LaunchMode.CONTINUE)).isEqualTo("claude (continue)");
    }
}
