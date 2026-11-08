package com.github.krpkbhdn.ccmanager.preset.apply;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assumptions.assumeThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProjectFilesTest {

    @TempDir
    Path project;

    @Test
    void ordinaryPathResolvesInsideTheProject() {
        assertThat(ProjectFiles.resolve(project, "a/b.txt")).isEqualTo(project.toAbsolutePath().normalize().resolve("a/b.txt"));
    }

    @Test
    void escapingPathsAreRejected() {
        for (var bad : new String[] {"../x", "a/../../x", "/etc/x", "C:/x", "C:\\x", "..\\x", "", " "}) {
            assertThatThrownBy(() -> ProjectFiles.resolve(project, bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Test
    void windowsDeviceNamesTrailingDotsAndGitFoldersAreRejected() {
        for (var bad : new String[] {"CON", "a/nul.txt", "Com1", "LPT9.md", "aux.tar.gz", "dir./x", "a/name /x", ".git/config", "a/.GIT/hooks/x"}) {
            assertThatThrownBy(() -> ProjectFiles.resolve(project, bad)).as(bad).isInstanceOf(IllegalArgumentException.class);
        }
        assertThat(ProjectFiles.resolve(project, ".gitignore")).isNotNull();
        assertThat(ProjectFiles.resolve(project, "console/communal.txt")).isNotNull();
    }

    @Test
    void replaceKeepsASymlinkAndRewritesItsTarget() throws IOException {
        Files.writeString(project.resolve("AGENTS.md"), "old");
        try {
            Files.createSymbolicLink(project.resolve("CLAUDE.md"), Path.of("AGENTS.md"));
        } catch (IOException | UnsupportedOperationException e) {
            assumeThat(false).as("symlinks cannot be created here").isTrue();
        }

        ProjectFiles.replace(project.resolve("CLAUDE.md"), "new");

        assertThat(Files.isSymbolicLink(project.resolve("CLAUDE.md"))).isTrue();
        assertThat(project.resolve("AGENTS.md")).hasContent("new");
    }

    @Test
    void symlinkOutOfTheProjectIsRejected(@TempDir Path outside) throws IOException {
        try {
            Files.createSymbolicLink(project.resolve("link"), outside);
        } catch (IOException | UnsupportedOperationException e) {
            assumeThat(false).as("symlinks cannot be created here").isTrue();
        }

        assertThatThrownBy(() -> ProjectFiles.resolve(project, "link/file.txt")).isInstanceOf(IllegalArgumentException.class);
    }
}
