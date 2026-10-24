package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GitIndexServiceTest {

    @TempDir
    Path root;

    private GitCliSandbox sandbox;
    private GitIndexService service;

    private void prepare() {
        sandbox = GitCliSandbox.create(root);
        service = new GitIndexService(sandbox.runner);
    }

    private void prepareWithCommit() {
        prepare();
        sandbox.write("a.txt", "a\n");
        sandbox.commitAll("base");
    }

    private String staged() {
        return sandbox.git("diff", "--cached", "--name-only").strip();
    }

    private static CommitRequest message(String text) {
        return new CommitRequest(text, false, false, false);
    }

    @Test
    void stagesAndUnstagesPathsWithSpacesAndGlobCharacters() {
        prepareWithCommit();
        sandbox.write("with space.txt", "1\n");
        sandbox.write("[x].txt", "2\n");
        sandbox.write("x.txt", "3\n");

        service.stage(sandbox.repo, List.of("with space.txt", "[x].txt"));

        assertThat(staged().lines()).containsExactlyInAnyOrder("with space.txt", "[x].txt");
        service.unstage(sandbox.repo, List.of("[x].txt"));
        assertThat(staged()).isEqualTo("with space.txt");
    }

    @Test
    void stagesDeletionsAndAllChanges() throws IOException {
        prepareWithCommit();
        Files.delete(sandbox.repo.resolve("a.txt"));
        sandbox.write("b.txt", "b\n");

        service.stageAll(sandbox.repo);

        assertThat(staged().lines()).containsExactlyInAnyOrder("a.txt", "b.txt");
    }

    @Test
    void unstageWorksInRepositoryWithoutCommits() {
        prepare();
        sandbox.write("a.txt", "a\n");
        service.stage(sandbox.repo, List.of("a.txt"));

        service.unstage(sandbox.repo, List.of("a.txt"));

        assertThat(sandbox.git("ls-files")).isBlank();
        assertThat(sandbox.repo.resolve("a.txt")).exists();
    }

    @Test
    void largePathListsGoThroughStdin() {
        prepareWithCommit();
        var names = new ArrayList<String>();
        for (var i = 0; i < GitIndexService.MAX_PATHS_ON_COMMAND_LINE + 20; i++) {
            names.add("f" + i + ".txt");
            sandbox.write("f" + i + ".txt", "x\n");
        }

        service.stage(sandbox.repo, names);
        assertThat(staged().lines()).hasSize(names.size());
        service.unstage(sandbox.repo, names);

        assertThat(staged()).isEmpty();
    }

    @Test
    void discardRestoresTrackedFilesAndRemovesUntrackedFilesAndDirectories() {
        prepareWithCommit();
        sandbox.write("a.txt", "changed\n");
        sandbox.write("fresh.txt", "x\n");
        sandbox.write("newdir/deep/f.txt", "y\n");

        service.discard(sandbox.repo, List.of("a.txt", "fresh.txt", "newdir"));

        assertThat(sandbox.read("a.txt")).isEqualTo("a\n");
        assertThat(sandbox.repo.resolve("fresh.txt")).doesNotExist();
        assertThat(sandbox.repo.resolve("newdir")).doesNotExist();
    }

    @Test
    void discardRefusesPathsOutsideTheRepositoryAndGitDirectory() throws IOException {
        prepareWithCommit();
        var outside = Files.writeString(root.resolve("outside.txt"), "precious");

        for (var evil : List.of("../outside.txt", outside.toString(), ".git/config", "sub/../../outside.txt", "")) {
            var failure = catchThrowableOfType(GitException.class, () -> service.discard(sandbox.repo, List.of(evil)));
            assertThat(failure).as(evil).isNotNull();
        }
        assertThat(outside).hasContent("precious");
        assertThat(sandbox.repo.resolve(".git/config")).exists();
    }

    @Test
    void discardDoesNotFollowADirectoryLinkOutOfTheRepository() throws IOException {
        prepareWithCommit();
        var outsideDirectory = Files.createDirectory(root.resolve("elsewhere"));
        Files.writeString(outsideDirectory.resolve("keep.txt"), "keep");
        try {
            Files.createSymbolicLink(sandbox.repo.resolve("link"), outsideDirectory);
        } catch (IOException | UnsupportedOperationException e) {
            org.junit.jupiter.api.Assumptions.abort("symbolic links are not available");
        }

        assertThatThrownBy(() -> service.discard(sandbox.repo, List.of("link/keep.txt"))).isInstanceOf(GitException.class);
        service.discard(sandbox.repo, List.of("link"));

        assertThat(outsideDirectory.resolve("keep.txt")).hasContent("keep");
    }

    @Test
    void commitReturnsShortHashAndStoresMultiLineMessage() {
        prepareWithCommit();
        sandbox.write("b.txt", "b\n");
        service.stage(sandbox.repo, List.of("b.txt"));

        var hash = service.commit(sandbox.repo, message("subject\n\nbody line"));

        assertThat(sandbox.git("rev-parse", "HEAD")).startsWith(hash);
        assertThat(sandbox.git("log", "-1", "--format=%B").strip()).isEqualTo("subject\n\nbody line");
    }

    @Test
    void amendWithBlankMessageKeepsTheOldOne() {
        prepareWithCommit();
        sandbox.write("b.txt", "b\n");
        service.stage(sandbox.repo, List.of("b.txt"));

        service.commit(sandbox.repo, new CommitRequest("", true, false, false));

        assertThat(sandbox.git("log", "-1", "--format=%s").strip()).isEqualTo("base");
        assertThat(sandbox.git("rev-list", "--count", "HEAD").strip()).isEqualTo("1");
        assertThat(sandbox.git("show", "--name-only", "--format=", "HEAD")).contains("b.txt");
    }

    @Test
    void amendWithNewMessageReplacesIt() {
        prepareWithCommit();

        service.commit(sandbox.repo, new CommitRequest("renamed", true, false, false));

        assertThat(sandbox.git("log", "-1", "--format=%s").strip()).isEqualTo("renamed");
    }

    @Test
    void signOffAddsTrailer() {
        prepareWithCommit();
        sandbox.write("b.txt", "b\n");
        service.stage(sandbox.repo, List.of("b.txt"));

        service.commit(sandbox.repo, new CommitRequest("signed", false, true, false));

        assertThat(sandbox.git("log", "-1", "--format=%B")).contains("Signed-off-by: Test User <test@example.com>");
    }

    @Test
    void failingHookIsReportedAndSkippedByNoVerify() throws IOException {
        prepareWithCommit();
        var hook = sandbox.repo.resolve(".git/hooks/pre-commit");
        Files.writeString(hook, "#!/bin/sh\necho rejected by hook >&2\nexit 1\n");
        hook.toFile().setExecutable(true);
        sandbox.write("b.txt", "b\n");
        service.stage(sandbox.repo, List.of("b.txt"));

        var failure = catchThrowableOfType(GitException.class, () -> service.commit(sandbox.repo, message("blocked")));
        service.commit(sandbox.repo, new CommitRequest("forced", false, false, true));

        assertThat(failure.kind()).isEqualTo(Kind.HOOK_FAILED);
        assertThat(failure.output()).contains("rejected by hook");
        assertThat(sandbox.git("log", "-1", "--format=%s").strip()).isEqualTo("forced");
    }

    @Test
    void commitFailuresAreClassified() {
        prepareWithCommit();

        assertThat(catchThrowableOfType(GitException.class, () -> service.commit(sandbox.repo, message("nothing"))).kind())
                .isEqualTo(Kind.NOTHING_TO_COMMIT);
        assertThat(catchThrowableOfType(GitException.class, () -> service.commit(sandbox.repo, message("  "))).getMessage())
                .contains("empty");
    }
}
