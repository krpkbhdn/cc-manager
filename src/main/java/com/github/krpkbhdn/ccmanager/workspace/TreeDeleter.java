package com.github.krpkbhdn.ccmanager.workspace;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

/**
 * Deletes a directory tree without ever following a link out of it. Windows junctions look like plain directories to
 * the JDK, so every directory is compared with its real path; a link is unlinked, its target is left alone.
 */
public final class TreeDeleter {

    private TreeDeleter() {}

    public static void delete(Path target) throws IOException {
        Files.walkFileTree(target, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (isLink(dir)) {
                    Files.delete(dir);
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            /** A junction whose target is gone cannot be listed, but the link itself can still be removed. */
            @Override
            public FileVisitResult visitFileFailed(Path file, IOException failure) throws IOException {
                if (failure instanceof NoSuchFileException && Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                    Files.delete(file);
                    return FileVisitResult.CONTINUE;
                }
                throw failure;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException failure) throws IOException {
                if (failure != null) {
                    throw failure;
                }
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static boolean isLink(Path directory) throws IOException {
        var parent = directory.toAbsolutePath().getParent();
        return parent != null && !directory.toRealPath().equals(parent.toRealPath().resolve(directory.getFileName()));
    }
}
