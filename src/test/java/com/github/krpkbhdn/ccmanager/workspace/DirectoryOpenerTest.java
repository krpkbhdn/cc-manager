package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DirectoryOpenerTest {

    @TempDir
    Path temp;

    @Test
    void openRejectsMissingDirectoryBeforeLaunchingAnything() {
        var opener = new DirectoryOpener(new FakeHostEnvironment(HostEnvironment.OperatingSystem.LINUX, temp));
        var missing = temp.resolve("nope");

        assertThatThrownBy(() -> opener.open(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Directory does not exist")
                .hasMessageContaining(missing.toString());
    }
}
