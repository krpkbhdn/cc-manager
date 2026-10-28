package com.github.krpkbhdn.ccmanager.app.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.claude.mcp.McpTool;
import com.github.krpkbhdn.ccmanager.git.GitBranchService;
import com.github.krpkbhdn.ccmanager.git.GitException;
import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitIndexService;
import com.github.krpkbhdn.ccmanager.git.GitLog;
import com.github.krpkbhdn.ccmanager.git.GitLogService;
import com.github.krpkbhdn.ccmanager.git.GitMisc.PushOptions;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import com.github.krpkbhdn.ccmanager.git.GitStatus;
import com.github.krpkbhdn.ccmanager.git.GitStatus.Change;
import com.github.krpkbhdn.ccmanager.git.GitStatusService;
import com.github.krpkbhdn.ccmanager.git.GitSyncService;
import com.github.krpkbhdn.ccmanager.git.GitWorktreeService;
import com.github.krpkbhdn.ccmanager.project.Project;
import com.github.krpkbhdn.ccmanager.project.ProjectService;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import tools.jackson.databind.json.JsonMapper;

class GitToolsTest {

    private static final Path PROJECT_PATH = Path.of("work", "app").toAbsolutePath();
    private static final Path REPO = PROJECT_PATH;

    private final JsonMapper mapper = JsonMapper.builder().build();
    private final ProjectService projects = mock(ProjectService.class);
    private final GitStatusService status = mock(GitStatusService.class);
    private final GitBranchService branches = mock(GitBranchService.class);
    private final GitIndexService index = mock(GitIndexService.class);
    private final GitSyncService sync = mock(GitSyncService.class);
    private final GitWorktreeService worktrees = mock(GitWorktreeService.class);

    GitToolsTest() {
        when(projects.find(7)).thenReturn(Optional.of(new Project(7, "app", PROJECT_PATH, Instant.EPOCH)));
        when(status.topLevel(PROJECT_PATH)).thenReturn(REPO);
    }

    private String call(AssistantMcpTool tool, String arguments) {
        return tool.call(mapper.readTree(arguments));
    }

    @Test
    void unknownProjectIsReportedWithItsId() {
        var tool = new GitPullTool(projects, status, sync, mapper);

        assertThatThrownBy(() -> call(tool, "{\"project\":99}")).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("99");
    }

    @Test
    void gitFailureBecomesToolErrorWithGitMessage() {
        doThrow(new GitException(Kind.DIRTY_TREE, "local changes would be overwritten", "")).when(branches).checkout(REPO, "dev", false);
        var tool = new GitCheckoutTool(projects, status, branches, mapper);

        assertThatThrownBy(() -> call(tool, "{\"project\":7,\"branch\":\"dev\"}")).isInstanceOf(IllegalStateException.class)
                .hasMessage("local changes would be overwritten");
    }

    @Test
    void checkoutWithCreateMakesTheBranch() {
        call(new GitCheckoutTool(projects, status, branches, mapper), "{\"project\":7,\"branch\":\"feat\",\"create\":true}");

        verify(branches).create(REPO, "feat", null, true);
    }

    @Test
    void statusListsBranchAndChanges() {
        var change = new Change("a.txt", null, GitStatus.Kind.NONE, GitStatus.Kind.MODIFIED);
        when(status.status(REPO)).thenReturn(new GitStatus(true, "main", "abc", false, "origin/main", 1, 0, GitStatus.Operation.NONE, 0, null, List.of(change)));

        var result = mapper.readTree(call(new GitStatusTool(projects, status, mapper), "{\"project\":7}"));

        assertThat(result.path("branch").asString()).isEqualTo("main");
        assertThat(result.path("changes").get(0).path("unstaged").asString()).isEqualTo("MODIFIED");
    }

    @Test
    void commitWithAllStagesChangedPathsFirst() {
        var change = new Change("a.txt", null, GitStatus.Kind.NONE, GitStatus.Kind.UNTRACKED);
        when(status.status(REPO)).thenReturn(new GitStatus(true, "main", "abc", false, null, 0, 0, GitStatus.Operation.NONE, 0, null, List.of(change)));
        when(index.commit(eq(REPO), any())).thenReturn("1a2b3c");

        var result = call(new GitCommitTool(projects, status, index, mapper), "{\"project\":7,\"message\":\"msg\",\"all\":true}");

        InOrder order = inOrder(index);
        order.verify(index).stage(REPO, List.of("a.txt"));
        order.verify(index).commit(eq(REPO), any());
        assertThat(result).contains("1a2b3c");
    }

    @Test
    void commitWithoutAllDoesNotStage() {
        call(new GitCommitTool(projects, status, index, mapper), "{\"project\":7,\"message\":\"msg\"}");

        verify(index, never()).stage(any(), any());
    }

    @Test
    void pushPublishesBranchWhenThereIsNoUpstreamAndNeverForces() {
        var plain = new PushOptions(false, false, false);
        doThrow(new GitException(Kind.NO_UPSTREAM, "no upstream", "")).when(sync).push(REPO, plain, null);

        call(new GitPushTool(projects, status, sync, mapper), "{\"project\":7}");

        verify(sync).push(REPO, new PushOptions(false, true, false), null);
    }

    @Test
    void addWorktreeDefaultsToSiblingFolderNamedAfterBranch() {
        call(new GitAddWorktreeTool(projects, status, worktrees, mapper), "{\"project\":7,\"branch\":\"feat/x\"}");

        verify(worktrees).add(REPO, "feat/x", null, REPO.resolveSibling("app-wt-feat-x"));
    }

    @Test
    void removeWorktreeNeverForces() {
        var folder = REPO.resolveSibling("app-wt-x");

        call(new GitRemoveWorktreeTool(projects, status, worktrees, mapper), "{\"project\":7,\"path\":\"" + folder.toString().replace("\\", "\\\\") + "\"}");

        verify(worktrees).remove(REPO, folder, false);
    }

    @Test
    void fetchTargetsTheGivenRemoteAndPrunes() {
        call(new GitFetchTool(projects, status, sync, mapper), "{\"project\":7,\"remote\":\"upstream\",\"prune\":true}");

        verify(sync).fetch(REPO, "upstream", true, null);
    }

    @Test
    void logListsRecentCommitsCompactly() {
        var log = mock(GitLogService.class);
        var commit = new GitLog.Commit("abcdef123", "abcdef1", List.of(), "Ann", "a@x", Instant.EPOCH, "Fix it", "", List.of());
        when(log.log(eq(REPO), any())).thenReturn(new GitLog.Page(List.of(new GitLog.Row(commit, 0, 0, 1, List.of())), false));

        var result = mapper.readTree(call(new GitLogTool(projects, status, log, mapper), "{\"project\":7,\"limit\":5}"));

        assertThat(result.get(0).path("subject").asString()).isEqualTo("Fix it");
        assertThat(result.get(0).path("hash").asString()).isEqualTo("abcdef1");
        verify(log).log(REPO, new GitLog.Query("HEAD", null, null, null, 0, 5));
    }

    @Test
    void diffIsTruncatedToTheLimit() {
        var lines = java.util.stream.IntStream.range(0, 5000).mapToObj(i -> new GitDiff.Line(GitDiff.LineKind.ADDED, "line " + i, null, i)).toList();
        when(status.diff(REPO, "a.txt", true)).thenReturn(new GitDiff.FileDiff("a.txt", null, GitDiff.ChangeType.ADDED, 5000, 0, false, lines));
        when(status.diff(REPO, "a.txt", false)).thenReturn(new GitDiff.FileDiff("a.txt", null, GitDiff.ChangeType.MODIFIED, 0, 0, false, List.of()));

        var result = call(new GitDiffTool(projects, status, mapper), "{\"project\":7,\"path\":\"a.txt\"}");

        assertThat(result).startsWith("--- a.txt").endsWith("... truncated").hasSizeLessThan(GitDiffTool.MAX_CHARS + 50);
    }

    @Test
    void toolsAreForTheAssistant() {
        assertThat(new GitStatusTool(projects, status, mapper)).isInstanceOf(AssistantMcpTool.class).isNotInstanceOf(McpTool.class);
    }
}
