package com.github.krpkbhdn.ccmanager.project;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.workspace.TreeDeleter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Owns a group's service directory (the session cwd) under the data dir: creates and deletes it and writes the app-owned CLAUDE.md that lists the members. */
@Component
public class GroupWorkspace {

    static final String MEMORY_FILE = "CLAUDE.md";
    private static final Logger log = LoggerFactory.getLogger(GroupWorkspace.class);
    private static final String GROUPS_DIR = "groups";

    private final Path groupsRoot;

    public GroupWorkspace(AppProperties properties) {
        this.groupsRoot = properties.dataDir().resolve(GROUPS_DIR).toAbsolutePath().normalize();
    }

    /** @return a new empty directory for a group */
    public Path create() {
        var directory = groupsRoot.resolve(UUID.randomUUID().toString());
        createDirectories(directory);
        return directory;
    }

    /** Only a directory inside the groups folder is ever deleted, whatever the project row says. */
    public void delete(Path groupDirectory) {
        var directory = groupDirectory.toAbsolutePath().normalize();
        if (!directory.startsWith(groupsRoot) || directory.equals(groupsRoot)) {
            return;
        }
        try {
            if (Files.exists(directory)) {
                TreeDeleter.delete(directory);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete the group folder " + directory, e);
        }
    }

    /** Deletes the group directories no group row points to any more, e.g. one a live session still locked when its group was removed. */
    public void sweep(Collection<Path> keptDirectories) {
        var kept = keptDirectories.stream().map(path -> path.toAbsolutePath().normalize()).collect(Collectors.toSet());
        if (!Files.isDirectory(groupsRoot)) {
            return;
        }
        try (var entries = Files.list(groupsRoot)) {
            entries.filter(entry -> !kept.contains(entry.toAbsolutePath().normalize())).forEach(entry -> {
                try {
                    delete(entry);
                } catch (RuntimeException e) {
                    log.warn("Could not delete the orphaned group folder {}", entry, e);
                }
            });
        } catch (IOException e) {
            log.warn("Could not list the groups folder {}", groupsRoot, e);
        }
    }

    /** Rewritten on every launch so it always matches the current members. */
    public void prepare(Path groupDirectory, List<Project> members) {
        createDirectories(groupDirectory);
        try {
            var temporary = Files.createTempFile(groupDirectory, MEMORY_FILE, ".tmp");
            Files.writeString(temporary, memory(members));
            Files.move(temporary, groupDirectory.resolve(MEMORY_FILE), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot prepare the group folder " + groupDirectory, e);
        }
    }

    private static void createDirectories(Path directory) {
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create the group folder " + directory, e);
        }
    }

    private static String memory(List<Project> members) {
        var list = members.stream().map(m -> "- " + codeSpan(m.name()) + ": " + codeSpan(m.path().toAbsolutePath().toString()))
                .collect(Collectors.joining("\n"));
        return """
                # Project group

                This folder only groups projects; it holds no code. The projects below are separate repositories,
                added to this session with --add-dir. Work in their real paths and run git inside each one.

                %s
                """.formatted(list);
    }

    /** One line, no backticks: a name with a newline cannot start a heading or an instruction of its own. */
    private static String codeSpan(String text) {
        return "`" + text.replaceAll("[\\p{Cntrl}\\s\\u2028\\u2029]+", " ").replace('`', '\'').strip() + "`";
    }
}
