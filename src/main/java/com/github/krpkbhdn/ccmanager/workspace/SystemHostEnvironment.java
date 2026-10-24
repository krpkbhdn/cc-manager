package com.github.krpkbhdn.ccmanager.workspace;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.springframework.stereotype.Component;

/** The real operating system. */
@Component
public class SystemHostEnvironment implements HostEnvironment {

    @Override
    public OperatingSystem os() {
        var name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.contains("win")) {
            return OperatingSystem.WINDOWS;
        }
        return name.contains("mac") ? OperatingSystem.MACOS : OperatingSystem.LINUX;
    }

    @Override
    public Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    @Override
    public String env(String name) {
        return System.getenv(name);
    }

    @Override
    public List<Path> pathDirectories() {
        var path = env("PATH");
        if (path == null || path.isBlank()) {
            return List.of();
        }
        return Arrays.stream(path.split(File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(SystemHostEnvironment::toPathOrNull)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Override
    public boolean isFile(Path path) {
        return Files.isRegularFile(path);
    }

    @Override
    public boolean isDirectory(Path path) {
        return Files.isDirectory(path);
    }

    @Override
    public List<Path> childDirectoriesStartingWith(Path parent, String prefix) {
        if (!Files.isDirectory(parent)) {
            return List.of();
        }
        try (Stream<Path> children = Files.list(parent)) {
            return children
                    .filter(Files::isDirectory)
                    .filter(child -> child.getFileName().toString().startsWith(prefix))
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    private static Path toPathOrNull(String entry) {
        try {
            return Path.of(entry.strip().replace("\"", ""));
        } catch (InvalidPathException e) {
            return null;
        }
    }
}
