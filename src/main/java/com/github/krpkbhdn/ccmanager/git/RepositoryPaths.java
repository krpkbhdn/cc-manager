package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.InvalidPathException;
import java.util.List;

/** Validates user-supplied paths so no git or delete operation can reach outside the repository or into .git. */
final class RepositoryPaths {

    private static final String GIT_DIRECTORY = ".git";

    private RepositoryPaths() {}

    /** @return the paths with '/' separators; @throws GitException (NOT_FOUND) for blank, absolute, escaping or .git paths */
    static List<String> validate(List<String> paths) {
        return paths.stream().map(RepositoryPaths::validate).toList();
    }

    /** Resolves a validated path to a location whose real parent chain is inside the repository (symlinks included). */
    static Path resolveInside(Path repo, String path) {
        var root = realPath(repo);
        var target = root.resolve(validate(path)).normalize();
        var parent = target.getParent();
        if (parent == null || !realPath(parent).startsWith(root)) {
            throw rejected(path);
        }
        return target;
    }

    private static String validate(String path) {
        if (path == null || path.isBlank() || path.indexOf('\0') >= 0) {
            throw rejected(path);
        }
        try {
            var relative = Path.of(path);
            var normalized = relative.normalize();
            if (relative.isAbsolute() || relative.getRoot() != null || normalized.startsWith("..") || normalized.toString().isEmpty()
                    || GIT_DIRECTORY.equalsIgnoreCase(normalized.getName(0).toString())) {
                throw rejected(path);
            }
            return File.separatorChar == '\\' ? normalized.toString().replace('\\', '/') : normalized.toString();
        } catch (InvalidPathException e) {
            throw rejected(path);
        }
    }

    private static Path realPath(Path path) {
        try {
            return Files.exists(path) ? path.toRealPath() : realPath(path.getParent());
        } catch (IOException e) {
            throw new GitException(Kind.OTHER, "Cannot resolve " + path, String.valueOf(e.getMessage()));
        }
    }

    private static GitException rejected(String path) {
        return new GitException(Kind.NOT_FOUND, "Path is not inside the repository: " + path, "");
    }
}
