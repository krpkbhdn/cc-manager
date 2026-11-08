package com.github.krpkbhdn.ccmanager.ui.git.model

import kotlin.math.abs

/** Index range where [anchor] occurs as consecutive lines (trailing whitespace ignored); of several, the one nearest [near]. */
fun locate(anchor: List<String>, haystack: List<String>, near: Int): IntRange? {
    if (anchor.isEmpty()) return null
    val wanted = anchor.map { it.trimEnd() }
    val text = haystack.map { it.trimEnd() }
    return (0..text.size - wanted.size)
        .filter { start -> wanted.indices.all { text[start + it] == wanted[it] } }
        .minByOrNull { abs(it - near) }
        ?.let { it until it + wanted.size }
}
