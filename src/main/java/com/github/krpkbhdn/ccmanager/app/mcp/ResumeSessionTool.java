package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AppControlRegistry;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class ResumeSessionTool extends AssistantTool {

    /** Resolved per call: ClaudeLauncher leads to HookServer, which needs AssistantMcpEndpoint, which needs this tool. */
    private final ObjectProvider<ClaudeLauncher> launcher;
    private final ProjectService projects;
    private final TeamService teams;
    private final AppControlRegistry app;

    ResumeSessionTool(ObjectProvider<ClaudeLauncher> launcher, ProjectService projects, TeamService teams, AppControlRegistry app) {
        this.launcher = launcher;
        this.projects = projects;
        this.teams = teams;
        this.app = app;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "resume_session";
    }

    @Override
    public String description() {
        return "Continues the last Claude conversation of a project or group in a new session, with the project's default agent team.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var control = app.require();
        var project = requiredProject(projects, arguments);
        var launched = launchWithDefaultTeam(launcher.getObject(), teams, project.id(), LaunchMode.CONTINUE, "");
        control.attachSession(launched);
        return "Session %s resumed in project %s.".formatted(launched.ccmSessionId(), project.id());
    }
}
