package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Pure builders for the claude command line and the child environment. */
final class ClaudeCommand {

    static final String ENV_TERM = "TERM";
    static final String ENV_COLORTERM = "COLORTERM";
    static final String ENV_SESSION = "CCM_SESSION";
    static final String TERM_VALUE = "xterm-256color";
    static final String COLORTERM_VALUE = "truecolor";
    /** Would turn off Claude colors or make it think it runs inside another agent or terminal host. */
    private static final List<String> INHERITED_ENV_TO_DROP = List.of(
            "NO_COLOR", "FORCE_COLOR", "CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "AI_AGENT", "TERMINAL_EMULATOR", "TERM_SESSION_ID");

    private ClaudeCommand() {}

    static List<String> launchArguments(Path executable, Path settingsFile, LaunchMode mode, ModelSelection selection) {
        List<String> command = base(executable, settingsFile);
        command.addAll(selection.arguments());
        switch (mode) {
            case NEW -> { }
            case CONTINUE -> command.add("--continue");
            case RESUME_PICKER -> command.add("--resume");
        }
        return command;
    }

    static List<String> attachArguments(Path executable, Path settingsFile, String backgroundSessionId) {
        List<String> command = base(executable, settingsFile);
        command.add("attach");
        command.add(backgroundSessionId);
        return command;
    }

    static Map<String, String> environment(Map<String, String> parent, String ccmSessionId) {
        Map<String, String> env = new HashMap<>(parent);
        INHERITED_ENV_TO_DROP.forEach(env::remove);
        env.put(ENV_TERM, TERM_VALUE);
        env.put(ENV_COLORTERM, COLORTERM_VALUE);
        env.put(ENV_SESSION, ccmSessionId);
        return env;
    }

    static String label(LaunchMode mode) {
        return switch (mode) {
            case NEW -> "claude";
            case CONTINUE -> "claude (continue)";
            case RESUME_PICKER -> "claude (resume)";
        };
    }

    private static List<String> base(Path executable, Path settingsFile) {
        List<String> command = new ArrayList<>();
        command.add(executable.toAbsolutePath().toString());
        command.add("--settings");
        command.add(settingsFile.toAbsolutePath().toString());
        return command;
    }
}
