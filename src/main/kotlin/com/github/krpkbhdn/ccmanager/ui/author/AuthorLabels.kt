package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus

fun statusLabel(status: AuthorStatus): String = when (status) {
    AuthorStatus.NOT_INSTALLED -> "Not installed"
    AuthorStatus.INSTALLED -> "Installed"
    AuthorStatus.UPDATE_AVAILABLE -> "Update available"
    AuthorStatus.DECLINED -> "Declined"
}

const val TEAM_REPLACE_WARNING = "Replaces the roles of your team with the author's version: edits you made to it are lost."

/** Updating the teams overwrites the roles, so it is never done by accident. */
fun replacesEdits(entry: AuthorEntry): Boolean = entry.component() == AuthorComponent.TEAMS && entry.status() == AuthorStatus.UPDATE_AVAILABLE

fun buttonLabel(status: AuthorStatus): String = when (status) {
    AuthorStatus.NOT_INSTALLED, AuthorStatus.DECLINED -> "Install"
    AuthorStatus.UPDATE_AVAILABLE -> "Update"
    AuthorStatus.INSTALLED -> "Reinstall"
}

fun versionTag(entry: AuthorEntry): String = when (entry.status()) {
    AuthorStatus.NOT_INSTALLED -> "New"
    AuthorStatus.UPDATE_AVAILABLE -> "Update to v${entry.bundledVersion()}"
    AuthorStatus.INSTALLED -> "v${entry.installedVersion()}"
    AuthorStatus.DECLINED -> "v${entry.bundledVersion()}"
}
