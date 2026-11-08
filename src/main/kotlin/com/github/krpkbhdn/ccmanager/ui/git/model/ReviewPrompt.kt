package com.github.krpkbhdn.ccmanager.ui.git.model

const val MAX_SNIPPET_LINES = 12
private const val ELLIPSIS = "…"
private const val INTRO = "Please address these review comments on the current changes. Fix each one or reply why not."

/** The single prompt that hands all [comments] to Claude, one numbered block per comment. */
fun reviewPrompt(comments: List<ReviewComment>): String =
    INTRO + "\n\n" + comments.mapIndexed { index, comment -> block(index + 1, comment) }.joinToString("\n\n")

private val controlChars = Regex("[\\u0000-\\u0009\\u000B-\\u001F\\u007F]")
private val backtickRun = Regex("`+")
private const val MIN_FENCE = 3

private fun block(number: Int, comment: ReviewComment): String {
    val capped = if (comment.snippet.size > MAX_SNIPPET_LINES) comment.snippet.take(MAX_SNIPPET_LINES) + ELLIPSIS else comment.snippet
    val snippet = capped.map { it.replace("\r", "").replace(controlChars, " ") }
    val longestRun = snippet.flatMap { line -> backtickRun.findAll(line).map { it.value.length }.toList() }.maxOrNull() ?: 0
    val fence = "`".repeat(maxOf(MIN_FENCE, longestRun + 1))
    val text = comment.text.replace("\r\n", "\n").replace(controlChars, " ")
    return "$number. ${reviewLocation(comment)}\n${fence}diff\n${snippet.joinToString("\n")}\n$fence\nComment: $text"
}

/** Path with the line range; removed-only comments name the old-side lines. */
fun reviewLocation(comment: ReviewComment): String {
    val lines = comment.lines
    val numbers = if (lines.first == lines.last) "${lines.first}" else "${lines.first}-${lines.last}"
    return if (comment.removedOnly) "${comment.path} (removed, old $numbers)" else "${comment.path}:$numbers"
}
