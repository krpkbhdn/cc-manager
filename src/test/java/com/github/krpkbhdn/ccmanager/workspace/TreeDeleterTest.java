package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TreeDeleterTest {

    @TempDir
    Path temp;

    @Test
    void deletesFilesAndNestedDirectories() throws IOException {
        var root = Files.createDirectories(temp.resolve("root").resolve("a").resolve("b"));
        Files.writeString(root.resolve("f.txt"), "x");

        TreeDeleter.delete(temp.resolve("root"));

        assertThat(temp.resolve("root")).doesNotExist();
    }

    @Test
    void aLinkIsUnlinkedAndItsTargetKept() throws Exception {
        var target = Files.createDirectories(temp.resolve("target"));
        Files.writeString(target.resolve("keep.txt"), "x");
        var root = Files.createDirectories(temp.resolve("root"));
        link(root.resolve("link"), target);

        TreeDeleter.delete(root);

        assertThat(root).doesNotExist();
        assertThat(target.resolve("keep.txt")).exists();
    }

    @Test
    void aDanglingLinkDoesNotStopTheDelete() throws Exception {
        var target = Files.createDirectories(temp.resolve("gone"));
        var root = Files.createDirectories(temp.resolve("root"));
        Files.writeString(root.resolve("a.txt"), "x");
        link(root.resolve("link"), target);
        Files.writeString(root.resolve("z.txt"), "x");
        Files.delete(target);

        TreeDeleter.delete(root);

        assertThat(root).doesNotExist();
    }

    private static void link(Path link, Path target) throws IOException, InterruptedException {
        if (System.getProperty("os.name").startsWith("Windows")) {
            var junction = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString()).redirectErrorStream(true).start();
            junction.getInputStream().readAllBytes();
            assumeTrue(junction.waitFor() == 0);
        } else {
            Files.createSymbolicLink(link, target);
        }
    }
}
