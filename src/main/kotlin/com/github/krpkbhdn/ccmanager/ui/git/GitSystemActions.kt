package com.github.krpkbhdn.ccmanager.ui.git

/** Things the Git tab asks the desktop for; paths are relative to the repository root. */
class GitSystemActions(
    val openFolder: (String) -> Unit,
    val openInIde: (String) -> Unit,
    val ideAvailable: Boolean,
)
