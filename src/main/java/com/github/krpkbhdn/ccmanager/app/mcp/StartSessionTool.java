package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class StartSessionTool extends AssistantTool {

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
    public boolean autoApproved() {
        return true;
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
        var launched = launchWithDefaultTeam(launcher.getObject(), teams, project, LaunchMode.NEW, prompt);
        control.attachSession(launched);
        return "Session %s started in project %s.".formatted(launched.ccmSessionId(), project);
    }
}
