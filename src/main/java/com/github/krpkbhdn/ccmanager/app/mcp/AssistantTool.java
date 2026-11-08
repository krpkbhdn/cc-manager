package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.app.AppControl;
import com.github.krpkbhdn.ccmanager.claude.launch.ClaudeLauncher;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchMode;
import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;
import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate;
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService;
import com.github.krpkbhdn.ccmanager.team.Team;
import com.github.krpkbhdn.ccmanager.team.TeamService;
import com.github.krpkbhdn.ccmanager.workspace.ProcessTree;
import java.util.List;
import java.util.Optional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Base of the Assistant's project and navigation tools: JSON results and the numeric {@code project} argument. */
abstract class AssistantTool implements AssistantMcpTool {

    static final String PROJECT_PROPERTY = "\"project\":{\"type\":\"integer\",\"description\":\"Project id from list_projects\"}";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    static String json(Object value) {
        return JSON.writeValueAsString(value);
    }

    static Project requiredProject(ProjectService projects, JsonNode arguments) {
        var id = ToolArguments.requiredId(arguments, "project");
        return projects.find(id).orElseThrow(() -> new IllegalArgumentException("Project not found: " + id));
    }

    static PromptTemplate requiredPrompt(PromptTemplateService prompts, long id) {
        return prompts.find(id).orElseThrow(() -> new IllegalArgumentException("Prompt not found: " + id));
    }

    static List<SessionSnapshot> liveSessions(SessionRegistry sessions, long projectId) {
        return sessions.sessions(projectId).stream().filter(s -> s.status() != AgentStatus.OFFLINE).toList();
    }

    /** Same choice as the UI: the project's default team, else the first team. */
    static Optional<Team> defaultTeam(TeamService teams, long project) {
        var chosen = Optional.ofNullable(teams.projectDefaults().get(project)).flatMap(teams::find);
        return chosen.or(() -> teams.list().stream().findFirst());
    }

    /** Cuts a long text and says so, so Claude never mistakes a partial text for the whole one. */
    static String capped(String text, int limit) {
        return text.length() <= limit ? text : text.substring(0, limit) + "\n[truncated: first " + limit + " of " + text.length() + " characters]";
    }

    /** Starts a session the way the UI does: with the project's default team when there is one; a non-empty prompt is its first task. */
    static LaunchedSession launchWithDefaultTeam(ClaudeLauncher claude, TeamService teams, long project, LaunchMode mode, String prompt) {
        var team = defaultTeam(teams, project);
        if (team.isEmpty()) {
            return prompt.isEmpty() ? claude.launch(project, mode) : claude.launchTask(project, prompt);
        }
        return prompt.isEmpty() ? claude.launch(project, mode, team.get()) : claude.launchTeam(project, team.get(), prompt);
    }

    /** Attaches in the UI; when that fails the freshly started process would have no owner, so it is killed (its exit watcher drops the session from the registry). */
    static void attachOrDestroy(AppControl control, LaunchedSession launched) {
        try {
            control.attachSession(launched);
        } catch (RuntimeException e) {
            ProcessTree.kill(launched.process());
            throw e;
        }
    }

    static Long optionalId(JsonNode arguments, String field) {
        return arguments.hasNonNull(field) ? ToolArguments.requiredId(arguments, field) : null;
    }
}
