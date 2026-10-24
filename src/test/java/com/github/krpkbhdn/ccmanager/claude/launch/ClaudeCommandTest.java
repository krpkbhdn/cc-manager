package com.github.krpkbhdn.ccmanager.claude.launch;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.team.RoleDraft;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamAgentsJson;
import com.github.krpkbhdn.ccmanager.team.TeamRole;
import java.nio.file.Path;
import java.util.List;
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
    void teamLaunchUsesLeadSelectionAgentsAddendumAndTaskAfterDoubleDash() {
        var lead = new RoleDraft("boss", "d", "Lead it.", "opus", "high", null, true);
        var tester = new RoleDraft("tester", "Checks.", "Test it.", "haiku", "low", null, false);
        var team = new Team(1, "T", List.of(new TeamRole(1, lead, 0), new TeamRole(2, tester, 1)));

        var command = ClaudeCommand.teamArguments(EXE, SETTINGS, team, "-fix it");

        assertThat(command).containsSubsequence("--settings", SETTINGS.toString(), "--model", "opus", "--effort", "high", "--agents")
                .contains(TeamAgentsJson.agents(team), TeamAgentsJson.leadAddendum(team))
                .endsWith("--append-system-prompt", TeamAgentsJson.leadAddendum(team), "--", "-fix it");
    }

    @Test
    void labelsAreShort() {
        assertThat(ClaudeCommand.label(LaunchMode.NEW)).isEqualTo("claude");
        assertThat(ClaudeCommand.label(LaunchMode.CONTINUE)).isEqualTo("claude (continue)");
    }
}
