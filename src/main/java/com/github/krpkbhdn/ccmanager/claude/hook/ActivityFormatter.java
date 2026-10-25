package com.github.krpkbhdn.ccmanager.claude.hook;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

/** Pure mapping from a hook event to the short human-readable line shown in activity views. */
public final class ActivityFormatter {

    public static final String INTERRUPTED = "Interrupted";

    private static final int TEXT_LIMIT = 80;
    private static final int MESSAGE_LIMIT = 120;
    private static final String ELLIPSIS = "…";
    private static final Set<String> ATTENTION_NOTIFICATIONS =
            Set.of("permission_prompt", "elicitation_dialog", "elicitation_url_dialog", "agent_needs_input");
    private static final Set<String> FILE_WRITING_TOOLS = Set.of("Edit", "MultiEdit", "Write", "NotebookEdit");
    private static final Map<String, Function<HookEvent, String>> TOOL_ARGUMENTS = Map.ofEntries(
            Map.entry("Bash", event -> toolInput(event, "command")),
            Map.entry("PowerShell", event -> toolInput(event, "command")),
            Map.entry("Read", event -> fileName(toolInput(event, "file_path"))),
            Map.entry("Edit", event -> fileName(toolInput(event, "file_path"))),
            Map.entry("MultiEdit", event -> fileName(toolInput(event, "file_path"))),
            Map.entry("Write", event -> fileName(toolInput(event, "file_path"))),
            Map.entry("NotebookEdit", event -> fileName(toolInput(event, "notebook_path"))),
            Map.entry("Grep", event -> toolInput(event, "pattern")),
            Map.entry("Glob", event -> toolInput(event, "pattern")),
            Map.entry("WebFetch", event -> toolInput(event, "url")),
            Map.entry("WebSearch", event -> toolInput(event, "query")),
            Map.entry("Agent", event -> toolInput(event, "description")),
            Map.entry("Task", event -> toolInput(event, "description")));

    private ActivityFormatter() {}

    /** Empty for events that are not worth showing (instructions loaded, idle nudges, ...). */
    public static Optional<String> describe(HookEvent event) {
        return Optional.ofNullable(switch (event.type()) {
            case SESSION_START -> "Session started";
            case SESSION_END -> "Session ended";
            case USER_PROMPT_SUBMIT -> "Prompt: " + truncate(event.text("prompt"), TEXT_LIMIT);
            case PRE_TOOL_USE -> preTool(event);
            case POST_TOOL_USE -> postTool(event);
            case POST_TOOL_USE_FAILURE -> "Tool failed: " + event.text("tool_name");
            case PERMISSION_REQUEST -> "Waiting for permission: " + event.text("tool_name");
            case NOTIFICATION -> notification(event);
            case ELICITATION -> "Waiting for input from " + event.text("mcp_server_name");
            case SUBAGENT_START -> "Subagent " + event.text("agent_type") + " started";
            case SUBAGENT_STOP -> "Subagent " + event.text("agent_type") + " finished";
            case PRE_COMPACT -> "Compacting context";
            case POST_COMPACT -> "Context compacted";
            case STOP -> finished(event);
            case STOP_FAILURE -> "Error: " + event.text("error");
            default -> null;
        });
    }

    /** Whether a Notification payload asks the user to act (as opposed to an informational nudge). */
    public static boolean needsAttention(HookEvent notification) {
        return ATTENTION_NOTIFICATIONS.contains(notification.text("notification_type"));
    }

    public static String truncate(String value, int limit) {
        var singleLine = value.replaceAll("\\s+", " ").strip();
        return singleLine.length() <= limit ? singleLine : singleLine.substring(0, limit) + ELLIPSIS;
    }

    /** The tool a PreToolUse starts, as "Bash: ./gradlew test"; file-writing tools included. */
    public static String running(HookEvent preToolUse) {
        var tool = preToolUse.text("tool_name");
        var argument = TOOL_ARGUMENTS.getOrDefault(tool, ignored -> "").apply(preToolUse);
        return argument.isEmpty() ? tool : tool + ": " + truncate(argument, TEXT_LIMIT);
    }

    private static String preTool(HookEvent event) {
        return FILE_WRITING_TOOLS.contains(event.text("tool_name")) ? null : running(event);
    }

    private static String postTool(HookEvent event) {
        var tool = event.text("tool_name");
        if (!FILE_WRITING_TOOLS.contains(tool)) {
            return null;
        }
        var path = toolInput(event, "file_path");
        var file = fileName(path.isEmpty() ? toolInput(event, "notebook_path") : path);
        return ("Write".equals(tool) ? "Wrote " : "Edited ") + file;
    }

    private static String notification(HookEvent event) {
        return needsAttention(event) ? "Needs attention: " + truncate(event.text("message"), TEXT_LIMIT) : null;
    }

    private static String finished(HookEvent event) {
        var message = event.text("last_assistant_message");
        return message.isEmpty() ? "Finished" : "Finished: " + truncate(message, MESSAGE_LIMIT);
    }

    private static String toolInput(HookEvent event, String field) {
        return event.payload().path("tool_input").path(field).asString("");
    }

    private static String fileName(String path) {
        var separator = Math.max(path.lastIndexOf('/'), path.lastIndexOf('\\'));
        return path.substring(separator + 1);
    }
}
