package com.github.krpkbhdn.ccmanager.app.mcp;

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.git.GitSyncService;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Component
class GitPullTool extends GitTool {

    private final GitSyncService sync;

    GitPullTool(ProjectService projects, GitStatusService status, GitSyncService sync, JsonMapper mapper) {
        super(projects, status, mapper);
        this.sync = sync;
    }

    @Override
    public String name() {
        return "git_pull";
    }

    @Override
    public String description() {
        return "Pulls the current branch of a project from its upstream, fast-forward only (fails instead of merging when the branches diverged).";
    }

    @Override
    public String inputSchema() {
        return "{\"type\":\"object\",\"properties\":{" + PROJECT_PROPERTY + "},\"required\":[\"project\"]}";
    }

    @Override
    String run(Path repo, JsonNode arguments) {
        sync.pull(repo, PullMode.FAST_FORWARD_ONLY, null);
        return "Pulled";
    }
}
