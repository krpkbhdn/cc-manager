package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.ui.state.fileName
import com.github.krpkbhdn.ccmanager.ui.state.parentDirectory

/** One visible row of a change list: a folder heading or a file; [depth] drives the indentation. */
sealed interface ChangeRow {
    val depth: Int

    data class Folder(val path: String, val label: String, override val depth: Int, val fileCount: Int, val collapsed: Boolean) : ChangeRow

    data class File(val change: GitStatus.Change, override val depth: Int, val name: String, val directory: String) : ChangeRow
}

private class Node(val name: String) {
    val folders = sortedMapOf<String, Node>()
    val files = mutableListOf<GitStatus.Change>()
    val fileCount: Int get() = files.size + folders.values.sumOf { it.fileCount }
}

/**
 * Flat: files sorted by path with their directory as a hint. Tree: folders first, chains of single-child folders
 * joined into one label ("src/main/java"), folders in [collapsedFolders] hide their content.
 */
fun changeRows(changes: List<GitStatus.Change>, flat: Boolean, collapsedFolders: Set<String> = emptySet()): List<ChangeRow> {
    if (flat) return changes.sortedBy { it.path }.map { ChangeRow.File(it, 0, fileName(it.path), parentDirectory(it.path)) }
    val root = Node("")
    changes.sortedBy { it.path }.forEach { change ->
        val folders = parentDirectory(change.path).split('/').filter { it.isNotEmpty() }
        val parent = folders.fold(root) { node, name -> node.folders.getOrPut(name) { Node(name) } }
        parent.files += change
    }
    return buildList { appendNode(root, "", 0, collapsedFolders) }
}

private fun MutableList<ChangeRow>.appendNode(node: Node, path: String, depth: Int, collapsed: Set<String>) {
    node.folders.values.forEach { child ->
        var tip = child
        var label = child.name
        while (tip.files.isEmpty() && tip.folders.size == 1) {
            tip = tip.folders.values.single()
            label += "/" + tip.name
        }
        val childPath = if (path.isEmpty()) label else "$path/$label"
        val isCollapsed = childPath in collapsed
        add(ChangeRow.Folder(childPath, label, depth, tip.fileCount, isCollapsed))
        if (!isCollapsed) appendNode(tip, childPath, depth + 1, collapsed)
    }
    node.files.forEach { add(ChangeRow.File(it, depth, fileName(it.path), "")) }
}

/** Paths of every file under [folderPath] among [changes], for folder-level stage / unstage. */
fun pathsUnder(changes: List<GitStatus.Change>, folderPath: String): List<String> =
    changes.map { it.path }.filter { it.startsWith("$folderPath/") }
