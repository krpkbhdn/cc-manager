package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class IdeLauncherTest {

    private final IdeDetector detector = mock(IdeDetector.class);
    private final CustomIdeService custom = mock(CustomIdeService.class);
    private final HostEnvironment host = new FakeHostEnvironment(HostEnvironment.OperatingSystem.WINDOWS, Path.of("C:\\Users\\u"));
    private final IdeLauncher launcher = new IdeLauncher(host, detector, custom);
    private final InstalledIde idea = new InstalledIde("intellij-idea", "IntelliJ IDEA", List.of("C:\\Toolbox\\idea.cmd"));
    private final InstalledIde code = new InstalledIde("vscode", "Visual Studio Code", List.of("C:\\code\\code.exe"));

    @Test
    void batchLauncherRejectsADirectoryWithPercentInsteadOfOpeningAnExpandedPath() {
        when(detector.detect()).thenReturn(List.of(idea));
        when(custom.list()).thenReturn(List.of());

        assertThatThrownBy(() -> launcher.open(Path.of("C:\\work\\%USERNAME%")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("%");
    }

    @Test
    void openWithUnknownIdIsRejectedWithItsId() {
        when(detector.detect()).thenReturn(List.of(idea));
        when(custom.list()).thenReturn(List.of());

        assertThatThrownBy(() -> launcher.open("nope", Path.of("C:\\work")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("nope");
    }

    @Test
    void openWithoutAnyIdeSaysSo() {
        when(detector.detect()).thenReturn(List.of());
        when(custom.list()).thenReturn(List.of());

        assertThat(launcher.available()).isFalse();
        assertThatThrownBy(() -> launcher.open(Path.of("C:\\work")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("No IDE");
    }

    @Test
    void listPutsDetectedBeforeCustomWithPrefixedId() {
        when(detector.detect()).thenReturn(List.of(idea));
        when(custom.list()).thenReturn(List.of(new CustomIde(7, "Mine", "C:\\mine.exe", "--new-window")));

        assertThat(launcher.list()).extracting(InstalledIde::id).containsExactly("intellij-idea", "custom-7");
        assertThat(launcher.list().getLast().command()).containsExactly("C:\\mine.exe", "--new-window");
    }

    @Test
    void resolveUsesPreferredIdAndFallsBackToTheFirstDetected() {
        when(detector.detect()).thenReturn(List.of(idea, code));
        when(custom.list()).thenReturn(List.of());

        assertThat(launcher.resolve(Optional.of("vscode"))).contains(code);
        assertThat(launcher.resolve(Optional.of("gone"))).contains(idea);
        assertThat(launcher.resolve(Optional.empty())).contains(idea);
    }
}
