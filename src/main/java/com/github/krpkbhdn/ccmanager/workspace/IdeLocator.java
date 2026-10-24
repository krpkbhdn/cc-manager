package com.github.krpkbhdn.ccmanager.workspace;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Finds an IntelliJ IDEA launcher for the current OS and returns the command prefix; the directory is appended by the caller. */
@Component
public class IdeLocator {

    private static final Pattern VERSION_PART = Pattern.compile("\\d+");
    private static final String WINDOWS_DEFAULT_PROGRAM_FILES = "C:\\Program Files";
    private static final String INSTALL_DIRECTORY_PREFIX = "IntelliJ IDEA";
    private static final List<String> PATH_LAUNCHERS = List.of("idea", "idea64", "idea.cmd", "idea64.exe", "idea.sh");
    private static final List<String> MAC_APPLICATIONS = List.of("IntelliJ IDEA", "IntelliJ IDEA CE");
    private static final List<Path> LINUX_FIXED = List.of(
            Path.of("/snap/bin/intellij-idea-ultimate"), Path.of("/snap/bin/intellij-idea-community"));

    /** A launcher is usable when probe exists; command is what to run (without the directory argument). */
    record Candidate(Path probe, List<String> command) {}

    public Optional<List<String>> find(HostEnvironment host) {
        return candidates(host).stream()
                .filter(candidate -> host.isFile(candidate.probe()) || host.isDirectory(candidate.probe()))
                .map(Candidate::command)
                .findFirst();
    }

    List<Candidate> candidates(HostEnvironment host) {
        var result = new ArrayList<Candidate>();
        host.pathCandidates(PATH_LAUNCHERS).forEach(path -> result.add(executable(host, path)));
        switch (host.os()) {
            case WINDOWS -> addWindows(host, result);
            case MACOS -> addMac(host, result);
            case LINUX -> addLinux(host, result);
        }
        return result;
    }

    private static void addWindows(HostEnvironment host, List<Candidate> result) {
        var localAppData = pathOf(host.env("LOCALAPPDATA"), host.home().resolve("AppData").resolve("Local"));
        result.add(executable(host, localAppData.resolve("JetBrains").resolve("Toolbox").resolve("scripts").resolve("idea.cmd")));
        var programFiles = pathOf(host.env("ProgramFiles"), Path.of(WINDOWS_DEFAULT_PROGRAM_FILES));
        addInstalled(host, result, programFiles.resolve("JetBrains"), INSTALL_DIRECTORY_PREFIX, "bin/idea64.exe");
        addInstalled(host, result, localAppData.resolve("Programs"), INSTALL_DIRECTORY_PREFIX, "bin/idea64.exe");
    }

    private static void addMac(HostEnvironment host, List<Candidate> result) {
        var applicationDirectories = List.of(Path.of("/Applications"), host.home().resolve("Applications"));
        for (var directory : applicationDirectories) {
            for (var application : MAC_APPLICATIONS) {
                result.add(new Candidate(directory.resolve(application + ".app"), List.of("open", "-a", application)));
            }
        }
        result.add(executable(host, toolboxScript(host)));
    }

    private static void addLinux(HostEnvironment host, List<Candidate> result) {
        result.add(executable(host, toolboxScript(host)));
        LINUX_FIXED.forEach(path -> result.add(executable(host, path)));
        addInstalled(host, result, Path.of("/opt"), "idea", "bin/idea.sh");
    }

    private static Path toolboxScript(HostEnvironment host) {
        return host.home().resolve(".local").resolve("share").resolve("JetBrains").resolve("Toolbox").resolve("scripts").resolve("idea");
    }

    private static void addInstalled(HostEnvironment host, List<Candidate> result, Path parent, String prefix, String relativeLauncher) {
        host.childDirectoriesStartingWith(parent, prefix).stream()
                .sorted((left, right) -> compare(versionOf(right), versionOf(left)))
                .forEach(dir -> result.add(executable(host, dir.resolve(relativeLauncher))));
    }

    private static Candidate executable(HostEnvironment host, Path launcher) {
        var name = launcher.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        var needsShell = host.windows() && (name.endsWith(".cmd") || name.endsWith(".bat"));
        return new Candidate(launcher, needsShell ? List.of("cmd", "/c", launcher.toString()) : List.of(launcher.toString()));
    }

    private static Path pathOf(String value, Path fallback) {
        return value == null || value.isBlank() ? fallback : Path.of(value);
    }

    /** Numeric parts of the directory name ("IntelliJ IDEA 2025.2.3" -> 2025.2.3) packed for comparison. */
    private static List<Integer> versionOf(Path directory) {
        return VERSION_PART.matcher(directory.getFileName().toString()).results()
                .map(match -> Integer.parseInt(match.group()))
                .toList();
    }

    private static int compare(List<Integer> left, List<Integer> right) {
        for (var i = 0; i < Math.min(left.size(), right.size()); i++) {
            var order = Integer.compare(left.get(i), right.get(i));
            if (order != 0) {
                return order;
            }
        }
        return Integer.compare(left.size(), right.size());
    }
}
