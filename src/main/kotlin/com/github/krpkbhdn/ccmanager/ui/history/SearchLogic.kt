package com.github.krpkbhdn.ccmanager.ui.history

import com.github.krpkbhdn.ccmanager.project.Project
import java.nio.file.InvalidPathException
import java.nio.file.Path
import java.time.Duration
import java.time.Instant

private const val MATCH_START = '\u0002'
private const val MATCH_END = '\u0003'

/** A piece of a search snippet; [match] pieces are the words the query hit. */
data class SnippetPart(val text: String, val match: Boolean)

/** Splits a snippet marked with char(2) before and char(3) after each match; an unclosed match runs to the end. */
fun parseSnippet(raw: String): List<SnippetPart> {
    val parts = mutableListOf<SnippetPart>()
    val text = StringBuilder()
    var inMatch = false
    fun flush() {
        if (text.isNotEmpty()) parts += SnippetPart(text.toString(), inMatch)
        text.clear()
    }
    for (char in raw) {
        when (char) {
            MATCH_START -> { flush(); inMatch = true }
            MATCH_END -> { flush(); inMatch = false }
            else -> text.append(char)
        }
    }
    flush()
    return parts
}

fun plainSnippet(raw: String): String = raw.filter { it != MATCH_START && it != MATCH_END }

enum class SearchPeriod(val label: String, private val span: Duration?) {
    ANY("Any time", null),
    DAY("24 hours", Duration.ofHours(24)),
    WEEK("7 days", Duration.ofDays(7)),
    MONTH("30 days", Duration.ofDays(30));

    /** Start of the period counted back from [now]; null for no lower bound. */
    fun from(now: Instant): Instant? = span?.let(now::minus)
}

/** The project whose directory holds [cwd], the deepest one when projects are nested; groups are not searched. */
fun projectOfCwd(cwd: String, projects: List<Project>): Project? {
    if (cwd.isEmpty()) return null
    val dir = try { Path.of(cwd).normalize() } catch (_: InvalidPathException) { return null }
    return projects.filter { !it.group && dir.startsWith(it.path.normalize()) }.maxByOrNull { it.path.nameCount }
}
