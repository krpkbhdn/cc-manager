package com.github.krpkbhdn.ccmanager.ui.git.model

/** The three lists of the Changes view. */
enum class ChangeGroup(val title: String) {
    CONFLICTS("Conflicts"),
    STAGED("Staged"),
    UNSTAGED("Changes"),
}

/** One line of the Changes list: a group heading, a folder in tree mode, or a file. */
sealed interface ChangeItem {
    data class Header(val group: ChangeGroup, val count: Int, val collapsed: Boolean) : ChangeItem

    data class Folder(val group: ChangeGroup, val row: ChangeRow.Folder) : ChangeItem {
        val key: String get() = folderKey(group, row.path)
    }

    data class File(val group: ChangeGroup, val row: ChangeRow.File) : ChangeItem
}

fun folderKey(group: ChangeGroup, path: String): String = "${group.name}/$path"

/** Flattens the sections into list lines; empty groups are omitted, collapsed groups keep only their heading. */
fun changeItems(sections: ChangeSections, flat: Boolean, collapsedGroups: Set<ChangeGroup>, collapsedFolders: Set<String>): List<ChangeItem> =
    buildList {
        listOf(ChangeGroup.CONFLICTS to sections.conflicts, ChangeGroup.STAGED to sections.staged, ChangeGroup.UNSTAGED to sections.unstaged)
            .filter { it.second.isNotEmpty() }
            .forEach { (group, changes) ->
                val collapsed = group in collapsedGroups
                add(ChangeItem.Header(group, changes.size, collapsed))
                if (collapsed) return@forEach
                val folders = collapsedFolders.filter { it.startsWith("${group.name}/") }.map { it.removePrefix("${group.name}/") }.toSet()
                changeRows(changes, flat, folders).forEach { row ->
                    add(
                        when (row) {
                            is ChangeRow.Folder -> ChangeItem.Folder(group, row)
                            is ChangeRow.File -> ChangeItem.File(group, row)
                        },
                    )
                }
            }
    }
