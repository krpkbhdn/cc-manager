package com.github.krpkbhdn.ccmanager.extension.skill;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** Reads the flat {@code key: value} lines of a SKILL.md front matter, including folded and literal multi-line values. */
final class SkillFrontmatter {

    private static final String FENCE = "---";
    private static final Pattern KEY_VALUE = Pattern.compile("^([A-Za-z][\\w-]*):\\s*(.*)$");
    private static final Pattern BLOCK_MARKER = Pattern.compile("^[>|][+-]?$");

    private SkillFrontmatter() {}

    /** @return the front matter fields, empty when the file has none */
    static Map<String, String> parse(String content) {
        var lines = content.lines().toList();
        var fields = new HashMap<String, String>();
        if (lines.isEmpty() || !lines.getFirst().strip().equals(FENCE)) {
            return fields;
        }
        var end = lines.subList(1, lines.size()).stream().map(String::strip).toList().indexOf(FENCE);
        var body = end < 0 ? lines.subList(1, lines.size()) : lines.subList(1, end + 1);
        for (int i = 0; i < body.size(); i++) {
            var matcher = KEY_VALUE.matcher(body.get(i));
            if (matcher.matches()) {
                var value = matcher.group(2).strip();
                var continuation = new StringBuilder();
                while (i + 1 < body.size() && (body.get(i + 1).startsWith(" ") || body.get(i + 1).startsWith("\t"))) {
                    continuation.append(' ').append(body.get(++i).strip());
                }
                fields.put(matcher.group(1), unquote(BLOCK_MARKER.matcher(value).matches() ? "" : value, continuation.toString()));
            }
        }
        return fields;
    }

    private static String unquote(String value, String continuation) {
        var joined = (value + continuation).strip();
        if (joined.length() >= 2 && (joined.startsWith("\"") && joined.endsWith("\"") || joined.startsWith("'") && joined.endsWith("'"))) {
            return joined.substring(1, joined.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return joined;
    }

    /** A SKILL.md with the two required fields; the description is always double-quoted so any text stays valid YAML. */
    static String render(String name, String description) {
        var quoted = description.replace("\\", "\\\\").replace("\"", "\\\"");
        return String.join("\n", List.of(FENCE, "name: " + name, "description: \"" + quoted + "\"", FENCE, "",
                "# " + name, "", "Describe what this skill does and when Claude should use it.", ""));
    }
}
