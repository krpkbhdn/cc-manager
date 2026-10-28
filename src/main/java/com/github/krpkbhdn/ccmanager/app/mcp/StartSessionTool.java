package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class StartSessionTool implements AssistantMcpTool {

    /** Resolved per call: ClaudeLauncher leads to HookServer, which needs AssistantMcpEndpoint, which needs this tool. */
    private final ObjectProvider<ClaudeLauncher> launcher;
    private final TeamService teams;
    private final AppControlRegistry app;

    StartSessionTool(ObjectProvider<ClaudeLauncher> launcher, TeamService teams, AppControlRegistry app) {
        this.launcher = launcher;
        this.teams = teams;
        this.app = app;
    }

    @Override
    public String name() {
        return "start_session";
    }

    @Override
    public String description() {
        return "Starts a new Claude session in a project, in the background of the UI, with the project's default agent team; the optional prompt is its first task.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{\"project\":{\"type\":\"integer\",\"description\":\"Project id\"},"
                + "\"prompt\":{\"type\":\"string\",\"description\":\"First task for the session\"}},\"required\":[\"project\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var control = app.require();
        var project = ToolArguments.requiredId(arguments, "project");
        var prompt = arguments.path("prompt").asString("").strip();
        var launched = launch(project, prompt);
        control.attachSession(launched);
        return "Session %s started in project %s.".formatted(launched.ccmSessionId(), project);
    }

    private LaunchedSession launch(long project, String prompt) {
        var claude = launcher.getObject();
        var team = defaultTeam(project);
        if (team.isEmpty()) {
            return prompt.isEmpty() ? claude.launch(project, LaunchMode.NEW) : claude.launchTask(project, prompt);
        }
        return prompt.isEmpty() ? claude.launch(project, LaunchMode.NEW, team.get()) : claude.launchTeam(project, team.get(), prompt);
    }

    /** Same choice as the UI: the project's default team, else the first team. */
    private Optional<Team> defaultTeam(long project) {
        var chosen = Optional.ofNullable(teams.projectDefaults().get(project)).flatMap(teams::find);
        return chosen.or(() -> teams.list().stream().findFirst());
    }
}
