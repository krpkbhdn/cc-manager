package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class IdeLocatorTest {

    private final IdeLocator locator = new IdeLocator();
    private final Path home = Path.of("/home/u");

    private java.util.Optional<java.util.List<String>> find(HostEnvironment host) {
        return locator.find(host, KnownIdes.ALL.getFirst());
    }

    @Test
    void windowsPrefersNewestInstalledVersionIncludingCommunityNaming() {
        var programs = Path.of("C:\\Program Files\\JetBrains");
        var host = new FakeHostEnvironment(OperatingSystem.WINDOWS, home)
                .withEnv("ProgramFiles", "C:\\Program Files")
                .withFile(programs.resolve("IntelliJ IDEA 2024.3").resolve("bin/idea64.exe"))
                .withFile(programs.resolve("IntelliJ IDEA Community Edition 2025.1").resolve("bin/idea64.exe"))
                .withFile(programs.resolve("IntelliJ IDEA 2025.2.3").resolve("bin/idea64.exe"));

        assertThat(find(host)).hasValueSatisfying(command ->
                assertThat(command.getFirst()).contains("IntelliJ IDEA 2025.2.3"));
    }

    @Test
    void windowsToolboxCmdScriptRunsDirectlySoAmpersandsInPathsStaySafe() {
        var script = Path.of("C:\\Users\\u\\AppData\\Local\\JetBrains\\Toolbox\\scripts\\idea.cmd");
        var host = new FakeHostEnvironment(OperatingSystem.WINDOWS, home)
                .withEnv("LOCALAPPDATA", "C:\\Users\\u\\AppData\\Local")
                .withFile(script);

        assertThat(find(host)).contains(java.util.List.of(script.toString()));
    }

    @Test
    void pathLauncherWinsOverFixedLocations() {
        var onPath = Path.of("/usr/bin/idea");
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, home)
                .withPath(Path.of("/usr/bin"))
                .withFile(onPath)
                .withFile(Path.of("/snap/bin/intellij-idea-community"));

        assertThat(find(host)).contains(java.util.List.of(onPath.toString()));
    }

    @Test
    void macUsesOpenWithApplicationName() {
        var host = new FakeHostEnvironment(OperatingSystem.MACOS, home)
                .withDirectory(Path.of("/Applications/IntelliJ IDEA CE.app"));

        assertThat(find(host)).contains(java.util.List.of("open", "-a", "IntelliJ IDEA CE"));
    }

    @Test
    void linuxFindsOptInstallation() {
        var host = new FakeHostEnvironment(OperatingSystem.LINUX, home)
                .withFile(Path.of("/opt/idea-IC-252/bin/idea.sh"));

        assertThat(find(host)).isPresent();
    }

    @Test
    void installFolderWithBuildTimestampDoesNotOverflowVersionParsing() {
        var programFiles = Path.of("C:\\Program Files\\JetBrains");
        var host = new FakeHostEnvironment(OperatingSystem.WINDOWS, home)
                .withEnv("ProgramFiles", "C:\\Program Files")
                .withFile(programFiles.resolve("IntelliJ IDEA 2025.2.3 20251010123456").resolve("bin/idea64.exe"));

        assertThat(find(host)).isPresent();
    }

    @Test
    void nothingFoundOnEmptyHost() {
        assertThat(find(new FakeHostEnvironment(OperatingSystem.LINUX, home))).isEmpty();
    }

    @Test
    void detectsIdeaOnThisMachineWhenInstalled() {
        var installed = Path.of("C:\\Program Files\\JetBrains");
        org.junit.jupiter.api.Assumptions.assumeTrue(java.nio.file.Files.isDirectory(installed));

        System.out.println("Resolved IDEA launcher: " + find(new SystemHostEnvironment()).orElse(null));
        assertThat(find(new SystemHostEnvironment())).isPresent();
    }
}
