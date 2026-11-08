package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.run.ActiveRun;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import com.github.krpkbhdn.ccmanager.run.RunService;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ListRunsTool implements AssistantMcpTool {

    private static final Pattern ASSIGNMENTS = Pattern.compile("^(?:[A-Za-z_]\\w*=(?:\"[^\"]*\"|'[^']*'|\\S*)\\s+)+");
    private static final Pattern EXECUTABLE = Pattern.compile("\"([^\"]*)\"|'([^']*)'|(\\S+)");

    private final RunConfigurationService configurations;
    private final RunService runs;

    ListRunsTool(RunConfigurationService configurations, RunService runs) {
        this.configurations = configurations;
        this.runs = runs;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "list_runs";
    }

    @Override
    public String description() {
        return "Lists the run configurations of a project with their id and the state of the latest run (running, exited with code, never run).";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"project\":{\"type\":\"integer\",\"description\":\"Project id\"}},\"required\":[\"project\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var list = configurations.list(ToolArguments.requiredId(arguments, "project"));
        if (list.isEmpty()) {
            return "No run configurations in this project.";
        }
        return list.stream().map(config -> "- id: %s | name: %s | command: %s | %s".formatted(config.id(), config.name(), executable(config.command()),
                state(runs.current(config.id())))).collect(Collectors.joining("\n"));
    }

    /** Arguments and leading NAME=value assignments may carry secrets, so only the executable's file name reaches the Assistant transcript. */
    private static String executable(String command) {
        var matcher = EXECUTABLE.matcher(ASSIGNMENTS.matcher(command.strip()).replaceFirst(""));
        var token = "";
        if (matcher.lookingAt()) {
            token = IntStream.rangeClosed(1, matcher.groupCount()).mapToObj(matcher::group).filter(Objects::nonNull).findFirst().orElse("");
        }
        return token.substring(Math.max(token.lastIndexOf('/'), token.lastIndexOf('\\')) + 1);
    }

    private static String state(Optional<ActiveRun> run) {
        return run.map(active -> active.running() ? "running" : "exited with code " + active.exitCode().getAsInt()).orElse("never run");
    }
}
