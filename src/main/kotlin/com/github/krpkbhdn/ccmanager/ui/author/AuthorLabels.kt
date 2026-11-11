package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus

fun statusLabel(status: AuthorStatus): String = when (status) {
    AuthorStatus.NOT_INSTALLED -> "New"
    AuthorStatus.INSTALLED -> "Installed"
    AuthorStatus.UPDATE_AVAILABLE -> "Update"
    AuthorStatus.DECLINED -> "Declined"
}

const val TEAM_REPLACE_WARNING = "Replaces the roles of your team with the author's version: edits you made to it are lost."

/** Installing a team overwrites the roles of one that already exists, so it is never pre-checked. */
fun replacesEdits(entry: AuthorEntry): Boolean = entry.replacesExisting()
