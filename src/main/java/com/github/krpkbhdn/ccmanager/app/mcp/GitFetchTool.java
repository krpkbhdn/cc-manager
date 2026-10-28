package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.git.GitSyncService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitFetchTool extends GitTool {

    private final GitSyncService sync;

    GitFetchTool(ProjectService projects, GitStatusService status, GitSyncService sync, JsonMapper mapper) {
        super(projects, status, mapper);
        this.sync = sync;
    }

    @Override
    public String name() {
        return "git_fetch";
    }

    @Override
    public String description() {
        return "Fetches a project's remotes without touching the working tree: one remote, or all when none is given; prune removes deleted remote branches.";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY
                + ",\"remote\":{\"type\":\"string\",\"description\":\"Remote name; all remotes when omitted\"}"
                + ",\"prune\":{\"type\":\"boolean\",\"description\":\"Drop remote-tracking branches deleted on the remote\"}"
                + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        sync.fetch(repo, optionalText(arguments, "remote"), flag(arguments, "prune"), null);
        return "Fetched";
    }
}
