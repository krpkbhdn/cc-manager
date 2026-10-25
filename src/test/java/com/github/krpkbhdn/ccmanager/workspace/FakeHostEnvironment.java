package com.github.krpkbhdn.ccmanager.workspace;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** In-memory host: files and directories exist only when added. */
public class FakeHostEnvironment implements HostEnvironment {

    private final OperatingSystem os;
    private final Path home;
    private final Map<String, String> env = new HashMap<>();
    private final Set<Path> files = new HashSet<>();
    private final Set<Path> directories = new HashSet<>();
    private final Map<Path, List<String>> fileLines = new HashMap<>();
    private final Map<Path, Path> links = new HashMap<>();
    private List<Path> pathDirectories = List.of();

    public FakeHostEnvironment(OperatingSystem os, Path home) {
        this.os = os;
        this.home = home;
    }

    public FakeHostEnvironment withPath(Path... directories) {
        pathDirectories = List.of(directories);
        return this;
    }

    public FakeHostEnvironment withEnv(String name, String value) {
        env.put(name, value);
        return this;
    }

    public FakeHostEnvironment withFile(Path file) {
        files.add(file);
        for (var parent = file.getParent(); parent != null; parent = parent.getParent()) {
            directories.add(parent);
        }
        return this;
    }

    public FakeHostEnvironment withLines(Path file, String... lines) {
        withFile(file);
        fileLines.put(file, List.of(lines));
        return this;
    }

    public FakeHostEnvironment withLink(Path link, Path target) {
        links.put(link, target);
        return this;
    }

    public FakeHostEnvironment withDirectory(Path directory) {
        directories.add(directory);
        return this;
    }

    @Override
    public OperatingSystem os() {
        return os;
    }

    @Override
    public Path home() {
        return home;
    }

    @Override
    public String env(String name) {
        return env.get(name);
    }

    @Override
    public List<Path> pathDirectories() {
        return pathDirectories;
    }

    @Override
    public boolean isFile(Path path) {
        return files.contains(path);
    }

    @Override
    public List<String> readLines(Path file) {
        return fileLines.getOrDefault(file, List.of());
    }

    @Override
    public Path realPath(Path path) {
        return links.getOrDefault(path, path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return directories.contains(path);
    }

    @Override
    public List<Path> childDirectoriesStartingWith(Path parent, String prefix) {
        return directories.stream()
                .filter(dir -> parent.equals(dir.getParent()) && dir.getFileName().toString().startsWith(prefix))
                .toList();
    }
}
