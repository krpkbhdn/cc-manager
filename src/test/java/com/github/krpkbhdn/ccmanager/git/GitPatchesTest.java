package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.ChangeType;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.FileDiff;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Every patch is applied by the real `git apply --cached`; the resulting index content is what is asserted. */
class GitPatchesTest {

    private static final String TWELVE_LINES = "1\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\n12\n";
    private static final String TWO_DISTANT_CHANGES = "one\n2\n3\n4\n5\n6\n7\n8\n9\n10\n11\ntwelve\n";

    @TempDir
    Path root;

    private GitCliSandbox sandbox;
    private GitStatusService status;
    private GitIndexService index;

    private void prepare() {
        sandbox = GitCliSandbox.create(root);
        status = new GitStatusService(sandbox.runner);
        index = new GitIndexService(sandbox.runner);
    }

    private void commitFile(String name, String content) {
        sandbox.write(name, content);
        sandbox.commitAll("add " + name);
    }

    private FileDiff diff(String path, boolean staged) {
        return status.diff(sandbox.repo, path, staged);
    }

    private String indexContent(String path) {
        return sandbox.git("show", ":" + path);
    }

    private static int lineIndex(FileDiff diff, LineKind kind, String text) {
        for (var i = 0; i < diff.lines().size(); i++) {
            Line line = diff.lines().get(i);
            if (line.kind() == kind && line.text().equals(text)) {
                return i;
            }
        }
        throw new AssertionError(kind + " " + text + " not in diff");
    }

    @Test
    void stagesOnlyTheChosenHunkOfSeveral() {
        prepare();
        commitFile("a.txt", TWELVE_LINES);
        sandbox.write("a.txt", TWO_DISTANT_CHANGES);

        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff("a.txt", false), 1), false);

        assertThat(indexContent("a.txt")).isEqualTo(TWELVE_LINES.replace("12\n", "twelve\n"));
    }

    @Test
    void unstagesOnlyTheChosenHunk() {
        prepare();
        commitFile("a.txt", TWELVE_LINES);
        sandbox.write("a.txt", TWO_DISTANT_CHANGES);
        sandbox.git("add", "a.txt");

        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff("a.txt", true), 0), true);

        assertThat(indexContent("a.txt")).isEqualTo(TWELVE_LINES.replace("12\n", "twelve\n"));
    }

    @Test
    void stagesSelectedLinesKeepingUnselectedRemovalsAsContext() {
        prepare();
        commitFile("a.txt", "a\nb\nc\nd\n");
        sandbox.write("a.txt", "a\nB\nC\nd\n");
        var diff = diff("a.txt", false);

        var patch = GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.REMOVED, "b"), lineIndex(diff, LineKind.ADDED, "B")));
        index.applyToIndex(sandbox.repo, patch, false);

        assertThat(indexContent("a.txt")).isEqualTo("a\nB\nc\nd\n");
    }

    @Test
    void stagedAdditionWithoutItsRemovalKeepsTheOldLine() {
        prepare();
        commitFile("a.txt", "a\nb\nc\n");
        sandbox.write("a.txt", "a\nB\nc\n");
        var diff = diff("a.txt", false);

        index.applyToIndex(sandbox.repo, GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.ADDED, "B"))), false);

        assertThat(indexContent("a.txt")).isEqualTo("a\nb\nB\nc\n");
    }

    @Test
    void unstagesSelectedLinesOnly() {
        prepare();
        commitFile("a.txt", "a\nb\nc\nd\n");
        sandbox.write("a.txt", "a\nB\nC\nd\n");
        sandbox.git("add", "a.txt");
        var diff = diff("a.txt", true);

        var patch = GitPatches.linesForUnstaging(diff, 0, Set.of(lineIndex(diff, LineKind.REMOVED, "c"), lineIndex(diff, LineKind.ADDED, "C")));
        index.applyToIndex(sandbox.repo, patch, true);

        assertThat(indexContent("a.txt")).isEqualTo("a\nB\nc\nd\n");
    }

    @Test
    void stagesPartOfANewFile() {
        prepare();
        commitFile("a.txt", "a\n");
        sandbox.write("new.txt", "x\ny\nz\n");
        var diff = diff("new.txt", false);

        index.applyToIndex(sandbox.repo, GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.ADDED, "x"), lineIndex(diff, LineKind.ADDED, "y"))), false);

        assertThat(indexContent("new.txt")).isEqualTo("x\ny\n");
    }

    @Test
    void stagesANewFileInRepositoryWithoutCommits() {
        prepare();
        sandbox.write("new.txt", "x\n");

        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff("new.txt", false), 0), false);

        assertThat(indexContent("new.txt")).isEqualTo("x\n");
    }

    @Test
    void stagesAWholeDeletedFileAndPartOfOne() {
        prepare();
        commitFile("gone.txt", "1\n2\n3\n");
        commitFile("partly.txt", "1\n2\n3\n");
        sandbox.git("rm", "-q", "--cached", "gone.txt", "partly.txt");
        sandbox.git("reset", "-q");
        java.util.stream.Stream.of("gone.txt", "partly.txt").forEach(name -> sandbox.repo.resolve(name).toFile().delete());

        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff("gone.txt", false), 0), false);
        var partly = diff("partly.txt", false);
        index.applyToIndex(sandbox.repo, GitPatches.lines(partly, 0, Set.of(lineIndex(partly, LineKind.REMOVED, "2"))), false);

        assertThat(sandbox.git("ls-files")).doesNotContain("gone.txt", "dev/null").contains("partly.txt");
        assertThat(indexContent("partly.txt")).isEqualTo("1\n3\n");
    }

    @Test
    void unstagesAChangeInsideARenamedFile() {
        prepare();
        commitFile("old.txt", "l1\nl2\nl3\nl4\nl5\nl6\n");
        sandbox.git("mv", "old.txt", "new.txt");
        sandbox.write("new.txt", "l1\nl2\nl3\nl4\nl5\nCHANGED\n");
        sandbox.git("add", "new.txt");
        var diff = diff("new.txt", true);

        assertThat(diff.oldPath()).isEqualTo("old.txt");
        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff, 0), true);

        assertThat(indexContent("new.txt")).isEqualTo("l1\nl2\nl3\nl4\nl5\nl6\n");
    }

    @Test
    void handlesMissingNewlineAtEndOfFile() {
        prepare();
        commitFile("a.txt", "a\nb");
        sandbox.write("a.txt", "a\nB");
        var whole = diff("a.txt", false);

        index.applyToIndex(sandbox.repo, GitPatches.hunk(whole, 0), false);
        assertThat(indexContent("a.txt")).isEqualTo("a\nB");
    }

    @Test
    void removalWithoutNewlineMarkerCanBeStagedWithItsReplacement() {
        prepare();
        commitFile("a.txt", "a\nb");
        sandbox.write("a.txt", "a\nB\nc\n");
        var diff = diff("a.txt", false);

        var patch = GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.REMOVED, "b"), lineIndex(diff, LineKind.ADDED, "B")));
        index.applyToIndex(sandbox.repo, patch, false);

        assertThat(indexContent("a.txt")).isEqualTo("a\nB\n");
    }

    @Test
    void droppedLineTakesItsNoNewlineMarkerWithIt() {
        prepare();
        commitFile("a.txt", "a\nb\n");
        sandbox.write("a.txt", "a\nb\nc\nd");
        var diff = diff("a.txt", false);

        index.applyToIndex(sandbox.repo, GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.ADDED, "c"))), false);

        assertThat(indexContent("a.txt")).isEqualTo("a\nb\nc\n");
    }

    @Test
    void stagesLinesOfCrlfFilesByteForByte() {
        prepare();
        commitFile("crlf.txt", "a\r\nb\r\nc\r\n");
        sandbox.writeBytes("crlf.txt", "a\r\nB\r\nc\r\nd\r\n".getBytes(StandardCharsets.UTF_8));
        var diff = diff("crlf.txt", false);

        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff, 0), false);

        assertThat(indexContent("crlf.txt")).isEqualTo("a\r\nB\r\nc\r\nd\r\n");
        assertThat(sandbox.git("diff", "--name-only")).isBlank();
    }

    @Test
    void stagesPartOfAFileWithSpacesInItsName() {
        prepare();
        commitFile("my file.txt", "a\nb\nc\n");
        sandbox.write("my file.txt", "a\nB\nc\n");

        index.applyToIndex(sandbox.repo, GitPatches.hunk(diff("my file.txt", false), 0), false);

        assertThat(indexContent("my file.txt")).isEqualTo("a\nB\nc\n");
    }

    @Test
    void rejectsImpossibleRequests() {
        prepare();
        commitFile("a.txt", "a\nb\n");
        sandbox.write("a.txt", "a\nB\n");
        sandbox.writeBytes("blob.bin", new byte[] {0, 1, 2});
        var diff = diff("a.txt", false);

        assertThatThrownBy(() -> GitPatches.hunk(diff, 3)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> GitPatches.lines(diff, 0, Set.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("No added or removed");
        assertThatThrownBy(() -> GitPatches.hunk(diff("blob.bin", false), 0)).isInstanceOf(IllegalArgumentException.class);
        assertThat(List.of(diff.lines().getFirst().kind())).containsExactly(LineKind.HUNK);
    }

    @Test
    void unstagesPartOfAStagedDeletionWithoutCreatingDevNull() {
        prepare();
        commitFile("gone.txt", "1\n2\n3\n");
        sandbox.git("rm", "-q", "gone.txt");
        var staged = diff("gone.txt", true);

        index.applyToIndex(sandbox.repo, GitPatches.linesForUnstaging(staged, 0, Set.of(lineIndex(staged, LineKind.REMOVED, "2"))), true);

        assertThat(sandbox.git("ls-files")).doesNotContain("dev/null");
        assertThat(indexContent("gone.txt")).isEqualTo("2\n");
    }

    @Test
    void stagingOnlyAnAppendedLineAfterAnUnterminatedLastLineKeepsTheLinesApart() {
        prepare();
        commitFile("a.txt", "p\nq");
        sandbox.write("a.txt", "p\nq\nR");
        var diff = diff("a.txt", false);

        index.applyToIndex(sandbox.repo, GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.ADDED, "R"))), false);

        assertThat(indexContent("a.txt")).isEqualTo("p\nq\nR");
    }

    @Test
    void stagingBothTheTerminatedLastLineAndTheAppendedOneDoesNotDuplicate() {
        prepare();
        commitFile("a.txt", "p\nq");
        sandbox.write("a.txt", "p\nq\nR");
        var diff = diff("a.txt", false);

        index.applyToIndex(sandbox.repo,
                GitPatches.lines(diff, 0, Set.of(lineIndex(diff, LineKind.ADDED, "q"), lineIndex(diff, LineKind.ADDED, "R"))), false);

        assertThat(indexContent("a.txt")).isEqualTo("p\nq\nR");
    }

    @Test
    void unstagingARemovedUnterminatedLastLineKeepsTheLinesAfterItApart() {
        prepare();
        commitFile("a.txt", "a\nb");
        sandbox.write("a.txt", "a\nB\nc\n");
        sandbox.git("add", "a.txt");
        var staged = diff("a.txt", true);

        index.applyToIndex(sandbox.repo, GitPatches.linesForUnstaging(staged, 0, Set.of(lineIndex(staged, LineKind.REMOVED, "b"))), true);

        assertThat(indexContent("a.txt")).isEqualTo("a\nb\nB\nc\n");
    }

    @Test
    void unstagingARemovedUnterminatedLastLineWithItsReplacementKeepsTheMarker() {
        prepare();
        commitFile("a.txt", "a\nb");
        sandbox.write("a.txt", "a\nB");
        sandbox.git("add", "a.txt");
        var staged = diff("a.txt", true);

        index.applyToIndex(sandbox.repo, GitPatches.linesForUnstaging(staged, 0,
                Set.of(lineIndex(staged, LineKind.REMOVED, "b"), lineIndex(staged, LineKind.ADDED, "B"))), true);

        assertThat(indexContent("a.txt")).isEqualTo("a\nb");
    }

    @Test
    void newFileKeepsItsExecutableModeInTheIndex() {
        prepare();
        var script = new FileDiff("run.sh", null, ChangeType.UNTRACKED, 1, 0, false,
                List.of(new Line(LineKind.HUNK, "@@ -0,0 +1 @@", null, null), new Line(LineKind.ADDED, "echo", null, 1)), "100755");
        sandbox.write("run.sh", "echo\n");

        index.applyToIndex(sandbox.repo, GitPatches.hunk(script, 0), false);

        assertThat(sandbox.git("ls-files", "-s", "run.sh")).startsWith("100755");
    }
}
