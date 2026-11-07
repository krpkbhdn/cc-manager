package com.github.krpkbhdn.ccmanager.git;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FolderIdentityTest {

    @TempDir
    Path root;

    @Test
    void anExistingFolderIsTheSameHoweverItIsSpelled() throws Exception {
        Path folder = Files.createDirectory(root.resolve("app"));

        assertThat(FolderIdentity.same(folder, root.resolve(".").resolve("app"))).isTrue();
        assertThat(FolderIdentity.same(folder, root.resolve("other"))).isFalse();
    }

    @Test
    void aMissingFolderIsComparedByItsNormalisedPath() {
        assertThat(FolderIdentity.same(root.resolve("gone"), root.resolve("a").resolve("..").resolve("gone"))).isTrue();
        assertThat(FolderIdentity.same(root.resolve("gone"), root.resolve("other"))).isFalse();
    }
}
