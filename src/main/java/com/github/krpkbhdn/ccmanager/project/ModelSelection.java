package com.github.krpkbhdn.ccmanager.project;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Model and reasoning effort a project's sessions start with; a null part means "whatever Claude's own settings say". */
public record ModelSelection(String model, String effort) {

    /** The levels claude --effort accepts (claude 2.1.310 --help); models that lack a level fall back to a lower one on their own. */
    public static final List<String> EFFORT_LEVELS = List.of("low", "medium", "high", "xhigh", "max");
    public static final ModelSelection DEFAULT = new ModelSelection(null, null);

    private static final Set<String> EFFORT_SET = Set.copyOf(EFFORT_LEVELS);
    private static final Pattern MODEL_NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._\\[\\]:/@-]{0,99}");

    /** @throws IllegalArgumentException with a user-readable message for a model name or effort claude would not take */
    public ModelSelection {
        model = blankToNull(model);
        effort = blankToNull(effort);
        if (model != null && !MODEL_NAME.matcher(model).matches()) {
            throw new IllegalArgumentException("Not a valid model name: " + model);
        }
        if (effort != null && !EFFORT_SET.contains(effort)) {
            throw new IllegalArgumentException("Unknown effort level: " + effort);
        }
    }

    /** The claude command line flags that apply this selection; empty for the default. */
    public List<String> arguments() {
        var flags = new ArrayList<String>();
        if (model != null) {
            flags.add("--model");
            flags.add(model);
        }
        if (effort != null) {
            flags.add("--effort");
            flags.add(effort);
        }
        return flags;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
