package com.github.krpkbhdn.ccmanager.ui.git.model

/** The sections of the Git tab, in the order of the segmented control. */
enum class GitSubView(val label: String) {
    CHANGES("Changes"),
    LOG("Log"),
    BRANCHES("Branches"),
    STASHES("Stashes"),
    TAGS("Tags"),
    WORKTREES("Worktrees"),
}
