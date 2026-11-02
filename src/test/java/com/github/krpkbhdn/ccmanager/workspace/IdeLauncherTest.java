package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdeLauncherTest {

    @Test
    void batchLauncherRejectsADirectoryWithPercentInsteadOfOpeningAnExpandedPath() {
        var locator = mock(IdeLocator.class);
        var host = new FakeHostEnvironment(HostEnvironment.OperatingSystem.WINDOWS, Path.of("C:\\Users\\u"));
        when(locator.find(host)).thenReturn(Optional.of(List.of("C:\\Toolbox\\idea.cmd")));

        assertThatThrownBy(() -> new IdeLauncher(host, locator).open(Path.of("C:\\work\\%USERNAME%")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("%");
    }
}
