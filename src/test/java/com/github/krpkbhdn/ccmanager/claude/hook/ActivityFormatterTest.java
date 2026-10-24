package com.github.krpkbhdn.ccmanager.claude.hook;

import static com.github.krpkbhdn.ccmanager.claude.HookEvents.of;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.claude.HookEvents;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

class ActivityFormatterTest {

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PreToolUse|{\"tool_name\":\"Bash\",\"tool_input\":{\"command\":\"git status\"}}|Bash: git status",
        "PreToolUse|{\"tool_name\":\"Read\",\"tool_input\":{\"file_path\":\"C:\\\\repo\\\\src\\\\App.java\"}}|Read: App.java",
        "PreToolUse|{\"tool_name\":\"mcp__db__query\",\"tool_input\":{\"sql\":\"select 1\"}}|mcp__db__query",
        "PostToolUse|{\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"/repo/src/App.java\",\"new_string\":\"secret\"}}|Edited App.java",
        "PostToolUse|{\"tool_name\":\"Write\",\"tool_input\":{\"file_path\":\"/repo/a.txt\"}}|Wrote a.txt",
        "PostToolUseFailure|{\"tool_name\":\"Bash\"}|Tool failed: Bash",
        "UserPromptSubmit|{\"prompt\":\"fix\\nthe   bug\"}|Prompt: fix the bug",
        "SubagentStart|{\"agent_type\":\"Explore\"}|Subagent Explore started",
        "SubagentStop|{\"agent_type\":\"Explore\"}|Subagent Explore finished",
        "PermissionRequest|{\"tool_name\":\"Bash\"}|Waiting for permission: Bash",
        "Notification|{\"notification_type\":\"permission_prompt\",\"message\":\"Claude needs your permission\"}|Needs attention: Claude needs your permission",
        "PreCompact|{}|Compacting context",
        "Stop|{}|Finished",
        "Stop|{\"last_assistant_message\":\"Done.\"}|Finished: Done.",
        "StopFailure|{\"error\":\"rate_limit\"}|Error: rate_limit",
    })
    void describesEventsInPlainWords(String eventName, String payload, String expected) {
        assertThat(ActivityFormatter.describe(of(eventName, payload))).contains(expected);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', value = {
        "PreToolUse|{\"tool_name\":\"Edit\",\"tool_input\":{\"file_path\":\"/a\"}}",
        "PostToolUse|{\"tool_name\":\"Bash\"}",
        "Notification|{\"notification_type\":\"idle_prompt\"}",
        "InstructionsLoaded|{}",
    })
    void staysSilentForNoise(String eventName, String payload) {
        assertThat(ActivityFormatter.describe(of(eventName, payload))).isEmpty();
    }

    @Test
    void truncatesLongValues() {
        var prompt = "x".repeat(200);

        var text = ActivityFormatter.describe(HookEvents.of("UserPromptSubmit", "{\"prompt\":\"" + prompt + "\"}")).orElseThrow();

        assertThat(text).isEqualTo("Prompt: " + "x".repeat(80) + "…");
    }
}
