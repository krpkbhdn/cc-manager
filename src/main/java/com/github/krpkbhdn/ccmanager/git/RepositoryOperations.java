package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitStatus.Operation;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Detects a stopped merge/rebase/cherry-pick/revert from the marker files git leaves in its directory. */
final class RepositoryOperations {

    private record Marker(String name, Operation operation) {}

    private static final List<Marker> MARKERS = List.of(
            new Marker("rebase-merge", Operation.REBASE),
            new Marker("rebase-apply", Operation.REBASE),
            new Marker("MERGE_HEAD", Operation.MERGE),
            new Marker("CHERRY_PICK_HEAD", Operation.CHERRY_PICK),
            new Marker("REVERT_HEAD", Operation.REVERT));

    private RepositoryOperations() {}

    static Operation detect(Path gitDirectory) {
        return MARKERS.stream()
                .filter(marker -> Files.exists(gitDirectory.resolve(marker.name())))
                .map(Marker::operation)
                .findFirst()
                .orElse(Operation.NONE);
    }
}
