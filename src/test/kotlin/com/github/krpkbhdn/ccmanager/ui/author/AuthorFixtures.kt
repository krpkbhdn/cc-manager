package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorArea
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorItem
import com.github.krpkbhdn.ccmanager.author.AuthorKind
import com.github.krpkbhdn.ccmanager.author.AuthorStatus

internal fun authorItem(
    id: String, area: AuthorArea = AuthorArea.CLAUDE_TOOLS, group: String = "Plugins", requires: List<String> = emptyList(),
    title: String = id, description: String = "", kind: AuthorKind = AuthorKind.PLUGIN,
) = AuthorItem(id, area, group, title, description, kind, 1, requires, null, id, null, null, null, null, null)

internal fun authorEntry(item: AuthorItem, status: AuthorStatus = AuthorStatus.NOT_INSTALLED, replaces: Boolean = false) =
    AuthorEntry(item, 0, status, emptyList(), replaces)
