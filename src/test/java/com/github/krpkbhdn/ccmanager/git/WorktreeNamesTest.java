package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WorktreeNamesTest {

    @TempDir
    Path root;

    @ParameterizedTest
    @ValueSource(strings = {"feature/x", "fix-1", "a.b", "v1.0"})
    void acceptsValidBranchNames(String name) {
        assertThat(WorktreeNames.branchProblem(name)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "a b", "a..b", "a~b", "a^b", "a:b", "a?b", "a*b", "a[b", "a\\b", "-a", "/a", "a/", "a.", "a.lock", "a@{b", "a//b"})
    void rejectsInvalidBranchNames(String name) {
        assertThat(WorktreeNames.branchProblem(name)).isPresent();
    }

    @ParameterizedTest
    @ValueSource(strings = {"@", "a/.b", ".a", "a.lock/b", "a\tb", "a\u007fb", "-", "--force"})
    void rejectsGitRefEdgeCaseBranchNames(String name) {
        assertThat(WorktreeNames.branchProblem(name)).isPresent();
    }

    @Test
    void rejectsNullBranchName() {
        assertThat(WorktreeNames.branchProblem(null)).isPresent();
    }

    @Test
    void acceptsMissingTargetWithExistingParentAndEmptyDirectory() throws Exception {
        var empty = Files.createDirectory(root.resolve("empty"));

        assertThat(WorktreeNames.pathProblem(root.resolve("new"))).isEmpty();
        assertThat(WorktreeNames.pathProblem(empty)).isEmpty();
    }

    @Test
    void rejectsBlankRelativeNonEmptyAndOrphanedTargets() throws Exception {
        var full = Files.createDirectory(root.resolve("full"));
        Files.writeString(full.resolve("f.txt"), "x");

        assertThat(WorktreeNames.pathProblem(null)).isPresent();
        assertThat(WorktreeNames.pathProblem(Path.of(""))).isPresent();
        assertThat(WorktreeNames.pathProblem(Path.of("relative"))).isPresent();
        assertThat(WorktreeNames.pathProblem(full)).isPresent();
        assertThat(WorktreeNames.pathProblem(root.resolve("missing").resolve("child"))).isPresent();
    }

    @Test
    void acceptsTargetWithSpacesInName() {
        assertThat(WorktreeNames.pathProblem(root.resolve("my repo-wt-x"))).isEmpty();
    }

    @Test
    void rejectsTargetThatIsAnExistingFile() throws Exception {
        var file = Files.writeString(root.resolve("file"), "x");

        assertThat(WorktreeNames.pathProblem(file)).isPresent();
    }

    @Test
    void rejectsFilesystemRootWithoutParent() {
        assertThat(WorktreeNames.pathProblem(root.getRoot())).isPresent();
    }
}
