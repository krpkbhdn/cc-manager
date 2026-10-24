package com.github.krpkbhdn.ccmanager.claude.hook;

import java.util.Arrays;

/** Every Claude Code hook event (docs/claude-code-protocol.md section 1). UNKNOWN keeps future events from being lost. */
public enum HookEventType {
    SESSION_START("SessionStart"), SETUP("Setup"), USER_PROMPT_SUBMIT("UserPromptSubmit"),
    USER_PROMPT_EXPANSION("UserPromptExpansion"), PRE_TOOL_USE("PreToolUse"), PERMISSION_REQUEST("PermissionRequest"),
    PERMISSION_DENIED("PermissionDenied"), POST_TOOL_USE("PostToolUse"), POST_TOOL_USE_FAILURE("PostToolUseFailure"),
    POST_TOOL_BATCH("PostToolBatch"), NOTIFICATION("Notification"), MESSAGE_DISPLAY("MessageDisplay"),
    SUBAGENT_START("SubagentStart"), SUBAGENT_STOP("SubagentStop"), TASK_CREATED("TaskCreated"),
    TASK_COMPLETED("TaskCompleted"), STOP("Stop"), STOP_FAILURE("StopFailure"), TEAMMATE_IDLE("TeammateIdle"),
    INSTRUCTIONS_LOADED("InstructionsLoaded"), CONFIG_CHANGE("ConfigChange"), CWD_CHANGED("CwdChanged"),
    DIRECTORY_ADDED("DirectoryAdded"), FILE_CHANGED("FileChanged"), WORKTREE_CREATE("WorktreeCreate"),
    WORKTREE_REMOVE("WorktreeRemove"), PRE_COMPACT("PreCompact"), POST_COMPACT("PostCompact"),
    PRE_MODEL_SWITCH("PreModelSwitch"), POST_MODEL_SWITCH("PostModelSwitch"), ELICITATION("Elicitation"),
    ELICITATION_RESULT("ElicitationResult"), SESSION_END("SessionEnd"), UNKNOWN("");

    private final String wireName;

    HookEventType(String wireName) {
        this.wireName = wireName;
    }

    /** Name used in settings.json and in hook_event_name. */
    public String wireName() {
        return wireName;
    }

    public static HookEventType of(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst().orElse(UNKNOWN);
    }
}
