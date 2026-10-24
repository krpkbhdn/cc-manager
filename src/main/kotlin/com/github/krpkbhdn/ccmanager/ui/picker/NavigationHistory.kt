package com.github.krpkbhdn.ccmanager.ui.picker

import java.nio.file.Path

/** Immutable browser history with a cursor, like a web browser's back/forward stack. */
class NavigationHistory private constructor(private val entries: List<Path>, private val index: Int) {
    constructor(start: Path) : this(listOf(start), 0)

    val current: Path get() = entries[index]
    val canGoBack: Boolean get() = index > 0
    val canGoForward: Boolean get() = index < entries.lastIndex
    val canGoUp: Boolean get() = current.parent != null

    /** Opening the directory already shown keeps the history unchanged. */
    fun visit(path: Path): NavigationHistory =
        if (path == current) this else NavigationHistory(entries.take(index + 1).plusElement(path), index + 1)

    fun back(): NavigationHistory = if (canGoBack) NavigationHistory(entries, index - 1) else this

    fun forward(): NavigationHistory = if (canGoForward) NavigationHistory(entries, index + 1) else this

    fun up(): NavigationHistory = current.parent?.let(::visit) ?: this
}
