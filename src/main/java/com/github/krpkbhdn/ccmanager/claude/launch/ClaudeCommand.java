package com.github.krpkbhdn.ccmanager.claude.launch;

import com.github.krpkbhdn.ccmanager.claude.hook.SessionFiles;
import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamAgentsJson;
import com.github.krpkbhdn.ccmanager.team.TeamRole;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Pure builders for the claude command line and the child environment. */
public final class ClaudeCommand {

    static final String ENV_TERM = "TERM";
    static final String ENV_COLORTERM = "COLORTERM";
    static final String ENV_SESSION = "CCM_SESSION";
    static final String ENV_ADDITIONAL_DIRECTORIES_CLAUDE_MD = "CLAUDE_CODE_ADDITIONAL_DIRECTORIES_CLAUDE_MD";
    private static final String SETTINGS_FLAG = "--settings";
    private static final String ENABLED = "1";
    static final String TERM_VALUE = "xterm-256color";
    static final String COLORTERM_VALUE = "truecolor";
    static final String ASSISTANT_SYSTEM_PROMPT = "You control Claude Code Manager through the cc-manager-app MCP tools. "
            + "Resolve project names with list_projects, act directly and report briefly. "
            + "Destructive actions are confirmed by the user. "
            + "For questions about Claude Code Manager itself, call get_help and answer from it.";
    private static final String ASSISTANT_TOOL_PREFIX = "mcp__cc-manager-app__";
    /** Would turn off Claude colors or make it think it runs inside another agent (which also switches off transcripts, so no --resume) or terminal host. */
    private static final List<String> INHERITED_ENV_TO_DROP = List.of(
            "NO_COLOR", "FORCE_COLOR", "CLAUDECODE", "CLAUDE_CODE_ENTRYPOINT", "CLAUDE_CODE_CHILD_SESSION",
            "CLAUDE_CODE_SESSION_ID", "CLAUDE_CODE_SESSION_ATTENDED", "CLAUDE_CODE_MESSAGING_SOCKET", "CLAUDE_CODE_MESSAGING_TOKEN", "CLAUDE_CODE_SSE_PORT", "CLAUDE_CODE_EXECPATH", "CLAUDE_PID",
            "AI_AGENT", "TERMINAL_EMULATOR", "TERM_SESSION_ID");

    private ClaudeCommand() {}

    static List<String> launchArguments(Path executable, SessionFiles files, Path pluginDir, LaunchMode mode, ModelSelection selection) {
        List<String> command = base(executable, files);
        addClaudeManager(command, files, pluginDir);
        command.addAll(selection.arguments());
        addModeFlag(command, mode);
        return command;
    }

    /** Claude does not persist --agents and the system prompt addendum in the transcript, so a continued or resumed conversation gets them again; the project's model and effort win over the lead's. */
    static List<String> launchArguments(Path executable, SessionFiles files, Path pluginDir, LaunchMode mode, ModelSelection selection, Team team) {
        List<String> command = base(executable, files);
        addClaudeManager(command, files, pluginDir);
        command.addAll(selection.orElse(leadSelection(team)).arguments());
        addTeam(command, team);
        addModeFlag(command, mode);
        return command;
    }

    /** The lead runs with its role's model and effort, the other roles as --agents, and the task as the first prompt (after "--" so it is never read as a flag). */
    static List<String> teamArguments(Path executable, SessionFiles files, Path pluginDir, Team team, String task) {
        List<String> command = base(executable, files);
        addClaudeManager(command, files, pluginDir);
        command.addAll(leadSelection(team).arguments());
        addTeam(command, team);
        command.addAll(List.of("--", task));
        return command;
    }

    /** A plain session with the project's model and effort and the task as the first prompt. */
    static List<String> taskArguments(Path executable, SessionFiles files, Path pluginDir, ModelSelection selection, String task) {
        List<String> command = base(executable, files);
        addClaudeManager(command, files, pluginDir);
        command.addAll(selection.arguments());
        command.addAll(List.of("--", task));
        return command;
    }

    /** The author setup session: the staged profile is readable through --add-dir and the first prompt only points at it. */
    static List<String> setupArguments(Path executable, SessionFiles files, Path pluginDir, String prompt, Path profileDirectory) {
        List<String> command = base(executable, files);
        addClaudeManager(command, files, pluginDir);
        command.addAll(List.of("--add-dir", profileDirectory.toAbsolutePath().toString(), "--", prompt));
        return command;
    }

    /** The assistant session: the cc-manager-app MCP config (no plugin, there is no project) and a pre-allowed tool list; each variadic flag is followed by another flag. */
    static List<String> assistantArguments(Path executable, SessionFiles files, ModelSelection selection,
            List<String> allowedTools) {
        List<String> command = base(executable, files);
        command.addAll(selection.arguments());
        command.addAll(List.of("--mcp-config", files.mcpConfig().toAbsolutePath().toString(),
                "--allowedTools", allowedToolsArgument(allowedTools), "--append-system-prompt", ASSISTANT_SYSTEM_PROMPT));
        return command;
    }

    private static String allowedToolsArgument(List<String> toolNames) {
        return toolNames.stream().map(name -> ASSISTANT_TOOL_PREFIX + name).collect(Collectors.joining(","));
    }

    static List<String> attachArguments(Path executable, SessionFiles files, String backgroundSessionId) {
        List<String> command = base(executable, files);
        command.add("attach");
        command.add(backgroundSessionId);
        return command;
    }

    static Map<String, String> environment(Map<String, String> parent, String ccmSessionId) {
        Map<String, String> env = terminalEnvironment(parent);
        env.put(ENV_SESSION, ccmSessionId);
        return env;
    }

    /** A group session also loads the CLAUDE.md of its --add-dir members. */
    static Map<String, String> groupEnvironment(Map<String, String> parent, String ccmSessionId) {
        Map<String, String> env = environment(parent, ccmSessionId);
        env.put(ENV_ADDITIONAL_DIRECTORIES_CLAUDE_MD, ENABLED);
        return env;
    }

    /** Inserted right after "--settings <file>" so it works for every launch kind; --add-dir is variadic, so each directory repeats the flag. */
    static void addDirectories(List<String> command, List<Path> directories) {
        List<String> flags = new ArrayList<>();
        directories.forEach(directory -> flags.addAll(List.of("--add-dir", directory.toAbsolutePath().toString())));
        command.addAll(command.indexOf(SETTINGS_FLAG) + 2, flags);
    }

    /** The parent environment without Claude-specific variables and with a color terminal. */
    public static Map<String, String> terminalEnvironment(Map<String, String> parent) {
        Map<String, String> env = new HashMap<>(parent);
        INHERITED_ENV_TO_DROP.forEach(env::remove);
        env.put(ENV_TERM, TERM_VALUE);
        env.put(ENV_COLORTERM, COLORTERM_VALUE);
        return env;
    }

    static String label(LaunchMode mode) {
        return switch (mode) {
            case NEW -> "claude";
            case CONTINUE -> "claude (continue)";
            case RESUME_PICKER -> "claude (resume)";
        };
    }

    static String label(LaunchMode mode, Team team) {
        return switch (mode) {
            case NEW -> "claude (" + team.name() + ")";
            case CONTINUE -> "claude (continue, " + team.name() + ")";
            case RESUME_PICKER -> "claude (resume, " + team.name() + ")";
        };
    }

    private static ModelSelection leadSelection(Team team) {
        return team.lead().map(TeamRole::selection).orElse(ModelSelection.DEFAULT);
    }

    private static void addTeam(List<String> command, Team team) {
        command.addAll(List.of("--agents", TeamAgentsJson.agents(team), "--append-system-prompt", TeamAgentsJson.leadAddendum(team)));
    }

    private static void addModeFlag(List<String> command, LaunchMode mode) {
        switch (mode) {
            case NEW -> { }
            case CONTINUE -> command.add("--continue");
            case RESUME_PICKER -> command.add("--resume");
        }
    }

    /** --mcp-config is variadic, so a flag follows it. Not for attach, which is a subcommand. */
    private static void addClaudeManager(List<String> command, SessionFiles files, Path pluginDir) {
        command.addAll(List.of("--mcp-config", files.mcpConfig().toAbsolutePath().toString(), "--plugin-dir", pluginDir.toAbsolutePath().toString()));
    }

    private static List<String> base(Path executable, SessionFiles files) {
        List<String> command = new ArrayList<>();
        command.add(executable.toAbsolutePath().toString());
        command.add(SETTINGS_FLAG);
        command.add(files.settings().toAbsolutePath().toString());
        return command;
    }
}
