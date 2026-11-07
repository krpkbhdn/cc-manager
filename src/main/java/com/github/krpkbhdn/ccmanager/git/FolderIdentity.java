package com.github.krpkbhdn.ccmanager.git;

import java.io.IOException;
import java.nio.file.Path;

/** Decides whether two paths name the same folder: symlinks, 8.3 names and case are resolved when the folder exists. */
public final class FolderIdentity {

    private FolderIdentity() {
    }

    /** The real path of an existing folder, else the absolute normalised path. */
    public static Path of(Path folder) {
        try {
            return folder.toRealPath();
        } catch (IOException | RuntimeException e) {
            return folder.toAbsolutePath().normalize();
        }
    }

    public static boolean same(Path a, Path b) {
        return of(a).equals(of(b));
    }
}
