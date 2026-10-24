package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.git.GitLog.Commit;
import com.github.krpkbhdn.ccmanager.git.GitLog.GraphLink;
import com.github.krpkbhdn.ccmanager.git.GitLog.Row;
import com.github.krpkbhdn.ccmanager.git.GraphState.Lane;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Pure lane assignment for a newest-first commit list: the first parent keeps the commit's lane, further parents take
 * (or join) other lanes, lanes free up when their branch ends. Lines that converge on a commit are drawn in the previous
 * row, so a link always ends on the lane of the dot it reaches.
 */
public final class GitGraphLayout {

    public static final int COLOR_SLOTS = 8;

    public record Result(List<Row> rows, GraphState stateAfterMarked) {}

    private final List<Lane> lanes;
    private int nextColor;
    private final List<MutableRow> rows = new ArrayList<>();

    private GitGraphLayout(GraphState start) {
        this.lanes = new ArrayList<>(start.lanes());
        this.nextColor = start.nextColor();
    }

    /** Lays out all commits; {@code stateAfterMarked} is the state after the first {@code markAfter} of them. */
    public static Result layout(List<Commit> commits, GraphState start, int markAfter) {
        var layout = new GitGraphLayout(start == null ? GraphState.EMPTY : start);
        var marked = layout.snapshot();
        for (var i = 0; i < commits.size(); i++) {
            layout.place(commits.get(i));
            if (i + 1 == markAfter) {
                marked = layout.snapshot();
            }
        }
        return new Result(layout.rows.stream().map(MutableRow::toRow).toList(), marked);
    }

    /** One lane, no lines: for filtered histories where parents are mostly missing from the list. */
    public static List<Row> linear(List<Commit> commits) {
        return commits.stream().map(commit -> new Row(commit, 0, 0, 1, List.of())).toList();
    }

    private void place(Commit commit) {
        var matches = lanesWaitingFor(commit.hash());
        var dot = matches.isEmpty() ? freeLane() : matches.getFirst();
        var color = matches.isEmpty() ? newColor() : lanes.get(dot).color();
        redirectConvergingLinks(matches, dot);
        var width = Math.max(lanes.size(), dot + 1);
        matches.forEach(lane -> lanes.set(lane, null));
        var continuing = IntStream.range(0, lanes.size()).filter(i -> lanes.get(i) != null).boxed().toList();
        var links = new ArrayList<GraphLink>();
        continueParents(commit, dot, color, links);
        width = Math.max(width, lanes.size());
        continuing.forEach(lane -> links.add(new GraphLink(lane, lane, lanes.get(lane).color())));
        trimFreeTail();
        rows.add(new MutableRow(commit, dot, color, width, links));
    }

    private void continueParents(Commit commit, int dot, int color, List<GraphLink> links) {
        for (var i = 0; i < commit.parents().size(); i++) {
            var parent = commit.parents().get(i);
            var existing = laneWaitingFor(parent);
            if (i == 0) {
                lanes.set(dot, new Lane(parent, color));
                links.add(new GraphLink(dot, dot, color));
            } else if (existing >= 0) {
                links.add(new GraphLink(dot, existing, lanes.get(existing).color()));
            } else {
                var lane = freeLane();
                var laneColor = newColor();
                lanes.set(lane, new Lane(parent, laneColor));
                links.add(new GraphLink(dot, lane, laneColor));
            }
        }
    }

    private void redirectConvergingLinks(List<Integer> matches, int dot) {
        if (rows.isEmpty() || matches.size() < 2) {
            return;
        }
        var previous = rows.getLast();
        for (var lane : matches.subList(1, matches.size())) {
            previous.links.replaceAll(link -> link.toLane() == lane ? new GraphLink(link.fromLane(), dot, link.color()) : link);
        }
    }

    private List<Integer> lanesWaitingFor(String hash) {
        return IntStream.range(0, lanes.size())
                .filter(i -> lanes.get(i) != null && lanes.get(i).hash().equals(hash))
                .boxed()
                .toList();
    }

    private int laneWaitingFor(String hash) {
        var matches = lanesWaitingFor(hash);
        return matches.isEmpty() ? -1 : matches.getFirst();
    }

    private int freeLane() {
        var free = lanes.indexOf(null);
        if (free >= 0) {
            return free;
        }
        lanes.add(null);
        return lanes.size() - 1;
    }

    private int newColor() {
        return nextColor++ % COLOR_SLOTS;
    }

    private void trimFreeTail() {
        while (!lanes.isEmpty() && lanes.getLast() == null) {
            lanes.removeLast();
        }
    }

    private GraphState snapshot() {
        return new GraphState(Collections.unmodifiableList(new ArrayList<>(lanes)), nextColor);
    }

    private record MutableRow(Commit commit, int lane, int color, int laneCount, List<GraphLink> links) {

        Row toRow() {
            return new Row(commit, lane, color, laneCount, List.copyOf(links));
        }
    }
}
