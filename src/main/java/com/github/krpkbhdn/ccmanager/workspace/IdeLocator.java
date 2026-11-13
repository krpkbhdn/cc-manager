package com.github.krpkbhdn.ccmanager.workspace;

import com.github.krpkbhdn.ccmanager.workspace.HostEnvironment.OperatingSystem;
import com.github.krpkbhdn.ccmanager.workspace.IdeDefinition.Location;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Finds the launcher of one known IDE for the current OS and returns the command prefix; the directory is appended by the caller. */
@Component
public class IdeLocator {

    private static final Pattern VERSION_PART = Pattern.compile("\\d{1,9}");

    /** A launcher is usable when probe exists; command is what to run (without the directory argument). */
    record Candidate(Path probe, List<String> command) {}

    public Optional<List<String>> find(HostEnvironment host, IdeDefinition ide) {
        return candidates(host, ide).stream()
                .filter(candidate -> host.isFile(candidate.probe()) || host.isDirectory(candidate.probe()))
                .map(Candidate::command)
                .findFirst();
    }

    List<Candidate> candidates(HostEnvironment host, IdeDefinition ide) {
        var result = new ArrayList<Candidate>();
        host.pathCandidates(ide.pathLaunchers()).forEach(path -> result.add(executable(path)));
        if (host.os() == OperatingSystem.MACOS) {
            addMacApplications(host, ide, result);
        }
        for (var location : ide.locations().getOrDefault(host.os(), List.of())) {
            addLocation(host, location, result);
        }
        return result;
    }

    private static void addMacApplications(HostEnvironment host, IdeDefinition ide, List<Candidate> result) {
        var applicationDirectories = List.of(Path.of("/Applications"), host.home().resolve("Applications"));
        for (var directory : applicationDirectories) {
            for (var application : ide.macApplications()) {
                result.add(new Candidate(directory.resolve(application + ".app"), List.of("open", "-a", application)));
            }
        }
    }

    private static void addLocation(HostEnvironment host, Location location, List<Candidate> result) {
        var directories = List.of(location.root().resolve(host).resolve(location.parent()));
        for (var prefix : location.directoryPrefixes()) {
            directories = directories.stream().flatMap(parent -> newestFirst(host, parent, prefix)).toList();
        }
        directories.forEach(directory -> result.add(executable(directory.resolve(location.launcher()))));
    }

    private static java.util.stream.Stream<Path> newestFirst(HostEnvironment host, Path parent, String prefix) {
        return host.childDirectoriesStartingWith(parent, prefix).stream()
                .sorted((left, right) -> compare(versionOf(right), versionOf(left)));
    }

    private static Candidate executable(Path launcher) {
        return new Candidate(launcher, List.of(launcher.toString()));
    }

    /** Numeric parts of the directory name ("IntelliJ IDEA 2025.2.3" -> 2025.2.3) packed for comparison; a longer digit run (a build timestamp) is split so it cannot overflow. */
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
