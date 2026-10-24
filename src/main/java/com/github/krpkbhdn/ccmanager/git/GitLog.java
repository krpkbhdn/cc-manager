package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff;
import java.time.Instant;
import java.util.List;

/** Commit history model: commits, refs, the lane layout of the graph, and one commit's details. */
public final class GitLog {

    private GitLog() {}

    public enum RefKind { HEAD, LOCAL_BRANCH, REMOTE_BRANCH, TAG }

    public record Ref(String name, RefKind kind) {}

    public record Commit(
            String hash,
            String shortHash,
            List<String> parents,
            String authorName,
            String authorEmail,
            Instant authorDate,
            String subject,
            String body,
            List<Ref> refs) {}

    /**
     * A line drawn between this row and the next one: from {@code fromLane} (at this row) to {@code toLane} (at the next
     * row), in palette slot {@code color}. Lines that merely continue a lane have fromLane == toLane.
     */
    public record GraphLink(int fromLane, int toLane, int color) {}

    /** {@code lane} is the column of this commit's dot, {@code color} its palette slot, {@code laneCount} the widest lane use. */
    public record Row(Commit commit, int lane, int color, int laneCount, List<GraphLink> links) {}

    public record Page(List<Row> rows, boolean hasMore) {}

    /** Null fields mean no filter; {@code branch} null = all refs. */
    public record Query(String branch, String author, String text, String path, int skip, int limit) {}

    public record Details(Commit commit, List<GitDiff.FileDiff> files) {}
}
