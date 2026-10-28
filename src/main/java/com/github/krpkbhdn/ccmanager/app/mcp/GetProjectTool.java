package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.git.GitStatus;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectGroupService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.run.RunConfigurationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;

@Component
class GetProjectTool extends AssistantTool {

    private final ProjectService projects;
    private final ProjectGroupService groups;
    private final GitStatusService git;
    private final SessionRegistry sessions;
    private final RunConfigurationService runConfigurations;

    GetProjectTool(ProjectService projects, ProjectGroupService groups, GitStatusService git, SessionRegistry sessions, RunConfigurationService runConfigurations) {
        this.projects = projects;
        this.groups = groups;
        this.git = git;
        this.sessions = sessions;
        this.runConfigurations = runConfigurations;
    }

    @Override
    public boolean autoApproved() {
        return true;
    }

    @Override
    public String name() {
        return "get_project";
    }

    @Override
    public String description() {
        return "Details of one project or group (members): path, current git branch, live sessions with their status, run configurations and the model selection.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    public String call(JsonNode arguments) {
        var project = requiredProject(projects, arguments);
        var result = new LinkedHashMap<String, Object>();
        result.put("id", project.id());
        result.put("name", project.name());
        result.put("path", project.path().toString());
        if (project.group()) {
            result.put("group", true);
            result.put("members", groups.members(project.id()).stream().map(m -> Map.of("id", m.id(), "name", m.name())).toList());
        } else {
            result.put("branch", branchOf(git.status(project.path())));
        }
        result.put("sessions", liveSessions(sessions, project.id()).stream()
                .map(s -> Map.of("id", s.ccmSessionId(), "status", s.status().name(), "title", String.valueOf(s.title()))).toList());
        result.put("runConfigurations", runConfigurations.list(project.id()).stream()
                .map(r -> Map.of("name", r.name(), "command", r.command(), "directory", r.directory().toString())).toList());
        result.put("model", project.modelSelection().model());
        result.put("effort", project.modelSelection().effort());
        return json(result);
    }

    private static String branchOf(GitStatus status) {
        if (!status.repository()) {
            return null;
        }
        return status.detached() ? "(detached " + status.head() + ")" : status.branch();
    }
}
