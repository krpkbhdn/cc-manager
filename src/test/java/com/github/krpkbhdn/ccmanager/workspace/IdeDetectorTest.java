package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class IdeDetectorTest {

    private final Path home = Path.of("/home/u");

    private static List<String> ids(IdeDetector detector) {
        return detector.detect().stream().map(InstalledIde::id).toList();
    }

    @Test
    void windowsFindsInstalledIdesInCatalogueOrder() {
        var host = new FakeHostEnvironment(OperatingSystem.WINDOWS, home)
                .withEnv("LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local")
                .withEnv("ProgramFiles", "C:\\Program Files")
                .withFile(Path.of("C:\\Users\\u\\AppData\\Local\\Programs\\Microsoft VS Code\\bin\\code.cmd"))
                .withFile(Path.of("C:\\Program Files\\JetBrains\\PyCharm 2025.2\\bin\\pycharm64.exe"))
                .withFile(Path.of("C:\\Program Files\\Microsoft Visual Studio\\2022\\Community\\Common7\\IDE\\devenv.exe"));

        var detector = new IdeDetector(host, new IdeLocator());

        assertThat(ids(detector)).containsExactly("pycharm", "vscode", "visual-studio");
        assertThat(detector.detect().get(1).command())
                .containsExactly("C:\\Users\\u\\AppData\\Local\\Programs\\Microsoft VS Code\\bin\\code.cmd");
    }

    @Test
    void linuxFindsCodeOnPathAndEclipseInOpt() {
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, home)
                .withPath(Path.of("/usr/bin"))
                .withFile(Path.of("/usr/bin/code"))
                .withFile(Path.of("/opt/eclipse/eclipse"));

        assertThat(ids(new IdeDetector(host, new IdeLocator()))).containsExactly("vscode", "eclipse");
    }

    @Test
    void macFindsApplicationBundlesThroughOpen() {
        var host = new FakeHostEnvironment(OperatingSystem.MACOS, home)
                .withDirectory(Path.of("/Applications/Visual Studio Code.app"))
                .withDirectory(Path.of("/Applications/Eclipse.app"));

        var detector = new IdeDetector(host, new IdeLocator());

        assertThat(ids(detector)).containsExactly("vscode", "eclipse");
        assertThat(detector.detect().getFirst().command()).containsExactly("open", "-a", "Visual Studio Code");
    }

    @Test
    void emptyHostFindsNothing() {
        assertThat(new IdeDetector(new FakeHostEnvironment(OperatingSystem.LINUX, home), new IdeLocator()).detect()).isEmpty();
    }

    @Test
    void resultIsCachedUntilRecheck() {
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, home).withPath(Path.of("/usr/bin"));
        var detector = new IdeDetector(host, new IdeLocator());
        assertThat(detector.detect()).isEmpty();

        host.withFile(Path.of("/usr/bin/code"));

        assertThat(detector.detect()).isEmpty();
        assertThat(detector.recheck()).hasSize(1);
        assertThat(detector.detect()).hasSize(1);
    }
}
