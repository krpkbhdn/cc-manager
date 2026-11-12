package com.github.krpkbhdn.ccmanager.web;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Lists the wanted computed properties in order and folds the four sides of margin, padding and border-width into the CSS shorthand. */
final class ComputedStyleSummary {

    private static final List<String> SIDES = List.of("top", "right", "bottom", "left");
    private static final List<String[]> SHORTHANDS = List.of(new String[] {"margin", ""}, new String[] {"padding", ""},
            new String[] {"border", "-width"});

    private ComputedStyleSummary() {
    }

    /** @return {@code "name value; name value"}, empty when nothing is present */
    static String of(List<String> keys, Map<String, String> computed) {
        var folded = new HashSet<String>();
        var parts = new ArrayList<String>();
        for (var key : keys) {
            if (!computed.containsKey(key) || folded.contains(key)) {
                continue;
            }
            var shorthand = SHORTHANDS.stream().filter(s -> SIDES.stream().anyMatch(side -> key.equals(longhand(s, side)))).findFirst();
            if (shorthand.isPresent() && SIDES.stream().allMatch(side -> computed.containsKey(longhand(shorthand.get(), side)))) {
                SIDES.forEach(side -> folded.add(longhand(shorthand.get(), side)));
                var values = SIDES.stream().map(side -> computed.get(longhand(shorthand.get(), side))).toList();
                parts.add(shorthand.get()[0] + shorthand.get()[1] + " " + shortest(values));
            } else {
                parts.add(key + " " + computed.get(key));
            }
        }
        return String.join("; ", parts);
    }

    private static String longhand(String[] shorthand, String side) {
        return shorthand[0] + "-" + side + shorthand[1];
    }

    /** top right bottom left folded as CSS does: one value when all are equal, two when top=bottom and right=left, three when right=left. */
    private static String shortest(List<String> sides) {
        var top = sides.get(0);
        var right = sides.get(1);
        var bottom = sides.get(2);
        var left = sides.get(3);
        if (top.equals(right) && right.equals(bottom) && bottom.equals(left)) {
            return top;
        }
        if (top.equals(bottom) && right.equals(left)) {
            return top + " " + right;
        }
        if (right.equals(left)) {
            return top + " " + right + " " + bottom;
        }
        return sides.stream().collect(Collectors.joining(" "));
    }
}
