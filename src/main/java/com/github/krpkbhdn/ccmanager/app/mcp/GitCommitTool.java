package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitIndexService;
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Change;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import com.github.krpkbhdn.ccmanager.git.SecretFiles;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitCommitTool extends GitTool {

    private final GitIndexService index;

    GitCommitTool(ProjectService projects, GitStatusService status, GitIndexService index, JsonMapper mapper) {
        super(projects, status, mapper);
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
        var skipped = List.<String>of();
        if (flag(arguments, "all")) {
            var byKind = status.status(repo).changes().stream().map(Change::path).collect(Collectors.partitioningBy(SecretFiles::looksSecret));
            skipped = byKind.get(true);
            index.stage(repo, byKind.get(false));
        }
        var hash = index.commit(repo, new CommitRequest(message, false, false, false));
        return "Committed " + hash + (skipped.isEmpty() ? "" : "; not staged because they look like secrets: " + String.join(", ", skipped));
    }
}
