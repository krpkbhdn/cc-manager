package com.github.krpkbhdn.ccmanager.team;

import com.github.krpkbhdn.ccmanager.project.ModelSelection;
import java.util.regex.Pattern;

/**
 * The editable part of a role. {@code description} tells the lead when to delegate; {@code tools} is a comma list of
 * Claude Code tool names or {@code mcp__server__*}, null to inherit all.
 */
public record RoleDraft(String name, String description, String prompt, String model, String effort, String tools, boolean lead) {

    public static final int MAX_NAME = 40;
    public static final int MAX_DESCRIPTION = 400;
    public static final int MAX_PROMPT = 8000;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");
    private static final Pattern TOOL_ENTRY = Pattern.compile("[A-Za-z][A-Za-z0-9_-]*(?:__\\*)?");

    /** @throws IllegalArgumentException with a user-readable message */
    public RoleDraft {
        name = name == null ? "" : name.strip();
        description = description == null ? "" : description.strip();
        prompt = prompt == null ? "" : prompt.strip();
        tools = normalizedTools(tools);
        var selection = new ModelSelection(model, effort);
        model = selection.model();
        effort = selection.effort();
        require(SLUG.matcher(name).matches() && name.length() <= MAX_NAME,
                "Role name must be lowercase letters, digits and dashes, up to " + MAX_NAME + " characters");
        require(!description.isEmpty() && description.length() <= MAX_DESCRIPTION,
                "Describe when the lead should use the role (up to " + MAX_DESCRIPTION + " characters)");
        require(!prompt.isEmpty() && prompt.length() <= MAX_PROMPT, "The role prompt must be 1 to " + MAX_PROMPT + " characters");
    }

    public RoleDraft withName(String newName) {
        return new RoleDraft(newName, description, prompt, model, effort, tools, lead);
    }

    public RoleDraft withLead(boolean newLead) {
        return new RoleDraft(name, description, prompt, model, effort, tools, newLead);
    }

    /** Whether claude accepts the entry in a subagent tool list: a tool name, or all tools of an MCP server as {@code mcp__server__*}. */
    public static boolean isToolEntry(String entry) {
        return TOOL_ENTRY.matcher(entry).matches();
    }

    public ModelSelection selection() {
        return new ModelSelection(model, effort);
    }

    private static String normalizedTools(String tools) {
        var entries = tools == null ? new String[0] : tools.split(",");
        var kept = new java.util.ArrayList<String>();
        for (var entry : entries) {
            var tool = entry.strip();
            if (tool.isEmpty()) {
                continue;
            }
            require(isToolEntry(tool), "Not a valid tool: " + tool + ". Use a tool name such as Read, or mcp__server__* for an MCP server.");
            kept.add(tool);
        }
        return kept.isEmpty() ? null : String.join(", ", kept);
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
