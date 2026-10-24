package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ClaudeCommandTest {

    private static final Path EXE = Path.of("claude").toAbsolutePath();
    private static final Path SETTINGS = Path.of("s.json").toAbsolutePath();

    @Test
    void newSessionHasOnlySettings() {
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.NEW, ModelSelection.DEFAULT))
                .containsExactly(EXE.toString(), "--settings", SETTINGS.toString());
    }

    @Test
    void continueAndResumeAddTheirFlag() {
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.CONTINUE, ModelSelection.DEFAULT)).endsWith("--continue");
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.RESUME_PICKER, ModelSelection.DEFAULT)).endsWith("--resume");
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
    void environmentDropsVariablesThatBreakClaudeColors() {
        Map<String, String> env = ClaudeCommand.environment(Map.of("NO_COLOR", "1", "CLAUDECODE", "1", "CLAUDE_CODE_CHILD_SESSION", "1", "CLAUDE_CONFIG_DIR", "d"), "ccm-1");

        assertThat(env).doesNotContainKeys("NO_COLOR", "CLAUDECODE", "CLAUDE_CODE_CHILD_SESSION").containsEntry("CLAUDE_CONFIG_DIR", "d");
    }

    @Test
    void selectionFlagsComeBeforeTheModeFlagSoResumeKeepsItsOptionalValueFree() {
        assertThat(ClaudeCommand.launchArguments(EXE, SETTINGS, LaunchMode.RESUME_PICKER, new ModelSelection("sonnet", "low")))
                .containsExactly(EXE.toString(), "--settings", SETTINGS.toString(), "--model", "sonnet", "--effort", "low", "--resume");
    }

    @Test
    void resumeKeepsTheConversationAndPutsSelectionFirst() {
        assertThat(ClaudeCommand.resumeArguments(EXE, SETTINGS, "ab-12", new ModelSelection("sonnet", "high")))
                .containsExactly(EXE.toString(), "--settings", SETTINGS.toString(), "--model", "sonnet", "--effort", "high", "--resume", "ab-12");
    }

    @Test
    void labelsAreShort() {
        assertThat(ClaudeCommand.label(LaunchMode.NEW)).isEqualTo("claude");
        assertThat(ClaudeCommand.label(LaunchMode.CONTINUE)).isEqualTo("claude (continue)");
    }
}
