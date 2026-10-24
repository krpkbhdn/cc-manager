package com.github.krpkbhdn.ccmanager.git;

import com.github.krpkbhdn.ccmanager.workspace.GitDiff.Line;
import com.github.krpkbhdn.ccmanager.workspace.GitDiff.LineKind;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Renders the body of one hunk for a line selection. A change block is a run of removals followed by additions; unselected
 * lines become context or vanish depending on the direction, and the other side's lines are slotted next to the selected ones
 * so a selected pair replaces in place instead of landing after the untouched lines.
 */
final class HunkSelection {

    /** One diff line with the "\ No newline at end of file" marker that follows it, if any. */
    private record Unit(int index, Line line, List<String> marker) {}

    private final Set<Integer> selected;
    private final boolean forUnstaging;
    private boolean touchedChange;

    HunkSelection(Set<Integer> selected, boolean forUnstaging) {
        this.selected = selected;
        this.forUnstaging = forUnstaging;
    }

    boolean touchedChange() {
        return touchedChange;
    }

    List<String> render(List<Line> lines, int from, int to) {
        var body = new ArrayList<String>();
        var removals = new ArrayList<Unit>();
        var additions = new ArrayList<Unit>();
        for (var unit : units(lines, from, to)) {
            switch (unit.line().kind()) {
                case REMOVED -> removals.add(unit);
                case ADDED -> additions.add(unit);
                default -> {
                    body.addAll(block(removals, additions));
                    removals.clear();
                    additions.clear();
                    body.add(GitPatches.raw(unit.line()));
                    body.addAll(unit.marker());
                }
            }
        }
        body.addAll(block(removals, additions));
        return body;
    }

    private static List<Unit> units(List<Line> lines, int from, int to) {
        var units = new ArrayList<Unit>();
        for (var i = from; i < to; i++) {
            var line = lines.get(i);
            if (line.kind() == LineKind.META && !units.isEmpty()) {
                var last = units.removeLast();
                var marker = new ArrayList<>(last.marker());
                marker.add(line.text());
                units.add(new Unit(last.index(), last.line(), marker));
            } else {
                units.add(new Unit(i, line, List.of()));
            }
        }
        return units;
    }

    private List<String> block(List<Unit> removals, List<Unit> additions) {
        var removalLines = removals.stream().map(unit -> change(unit, "-", !forUnstaging)).toList();
        var additionLines = additions.stream().map(unit -> change(unit, "+", forUnstaging)).toList();
        var result = new ArrayList<String>();
        if (forUnstaging) {
            var position = firstSelected(additions);
            additionLines.subList(0, position).forEach(result::addAll);
            removalLines.forEach(result::addAll);
            additionLines.subList(position, additionLines.size()).forEach(result::addAll);
        } else {
            var position = lastSelected(removals) + 1;
            removalLines.subList(0, position).forEach(result::addAll);
            additionLines.forEach(result::addAll);
            removalLines.subList(position, removalLines.size()).forEach(result::addAll);
        }
        return result;
    }

    private List<String> change(Unit unit, String sign, boolean unselectedStaysContext) {
        var lines = new ArrayList<String>();
        if (selected.contains(unit.index())) {
            touchedChange = true;
            lines.add(sign + unit.line().text());
        } else if (unselectedStaysContext) {
            lines.add(" " + unit.line().text());
        } else {
            return List.of();
        }
        lines.addAll(unit.marker());
        return lines;
    }

    private int firstSelected(List<Unit> units) {
        for (var i = 0; i < units.size(); i++) {
            if (selected.contains(units.get(i).index())) {
                return i;
            }
        }
        return 0;
    }

    private int lastSelected(List<Unit> units) {
        for (var i = units.size() - 1; i >= 0; i--) {
            if (selected.contains(units.get(i).index())) {
                return i;
            }
        }
        return units.size() - 1;
    }
}
