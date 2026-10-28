package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitStatus;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitStatusTool extends GitTool {

    private final GitStatusService status;

    GitStatusTool(ProjectService projects, GitStatusService status, JsonMapper mapper) {
        super(projects, status, mapper);
        this.status = status;
    }

    @Override
    public String name() {
        return "git_status";
    }

    @Override
    public String description() {
        return "Shows the git state of a project: current branch, upstream, ahead/behind, stopped operation and changed files (staged/unstaged kinds).";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        var state = status.status(repo);
        if (!state.repository()) {
            throw new IllegalStateException("Not a git repository: " + repo);
        }
        var result = new LinkedHashMap<String, Object>();
        result.put("branch", state.detached() ? "(detached " + state.head() + ")" : state.branch());
        result.put("upstream", state.upstream());
        result.put("ahead", state.ahead());
        result.put("behind", state.behind());
        result.put("operation", state.operation());
        result.put("stashes", state.stashCount());
        result.put("changes", state.changes().stream().map(GitStatusTool::change).toList());
        return json(result);
    }

    private static LinkedHashMap<String, Object> change(GitStatus.Change change) {
        var result = new LinkedHashMap<String, Object>();
        result.put("path", change.path());
        result.put("staged", change.staged());
        result.put("unstaged", change.unstaged());
        return result;
    }
}
