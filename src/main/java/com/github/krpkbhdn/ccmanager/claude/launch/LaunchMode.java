package com.github.krpkbhdn.ccmanager.claude.launch;

public enum LaunchMode {
    /** claude */
    NEW,
    /** claude --continue */
    CONTINUE,
    /** claude --resume (the CLI's own session picker) */
    RESUME_PICKER
}
