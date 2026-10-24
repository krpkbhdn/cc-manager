package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitException.Kind;

/** Guards against user-supplied revisions or names that git would read as options. */
final class GitRevisions {

    private GitRevisions() {}

    static String requireNotOption(String revision) {
        if (revision == null || revision.isBlank() || revision.startsWith("-")) {
            throw new GitException(Kind.NOT_FOUND, "Invalid revision: " + revision, "");
        }
        return revision;
    }
}
