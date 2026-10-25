package com.github.krpkbhdn.ccmanager.shell;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ShellLauncherTest {

    @Test
    void shellGetsTerminalVariablesAndNoClaudeSession() {
        var env = ShellLauncher.environment(Map.of("PATH", "/bin"));

        assertThat(env).containsEntry("PATH", "/bin").containsEntry("TERM", "xterm-256color").doesNotContainKey("CCM_SESSION");
    }

    @Test
    void shellDropsVariablesInheritedFromClaudeCode() {
        var env = ShellLauncher.environment(Map.of("PATH", "/bin", "CLAUDECODE", "1", "CLAUDE_CODE_ENTRYPOINT", "cli", "NO_COLOR", "1", "FORCE_COLOR", "1"));

        assertThat(env).containsKey("PATH").doesNotContainKeys("CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "NO_COLOR", "FORCE_COLOR");
    }
}
