package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitIndexService;
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Change;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitCommitTool extends GitTool {

    private final GitStatusService status;
    private final GitIndexService index;

    GitCommitTool(ProjectService projects, GitStatusService status, GitIndexService index, JsonMapper mapper) {
        super(projects, status, mapper);
        this.status = status;
        this.index = index;
    }

    @Override
    public String name() {
        return "git_commit";
    }

    @Override
    public String description() {
        return "Commits the staged changes of a project; with all=true stages every changed and untracked file first. Hooks run.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + ","
                + "\"message\":{\"type\":\"string\"},"
                + "\"all\":{\"type\":\"boolean\",\"description\":\"Stage everything first\"}},"
                + "\"required\":[\"project\",\"message\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        var message = requiredText(arguments, "message");
        if (flag(arguments, "all")) {
            index.stage(repo, status.status(repo).changes().stream().map(Change::path).toList());
        }
        var hash = index.commit(repo, new CommitRequest(message, false, false, false));
        return "Committed " + hash;
    }
}
