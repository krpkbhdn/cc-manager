package com.github.krpkbhdn.ccmanager.ui.state

private const val BASE_HIT = 10
private const val CONSECUTIVE_BONUS = 8
private const val WORD_START_BONUS = 12
private const val FIRST_CHAR_BONUS = 6
private const val GAP_PENALTY = 1

/** Result of a successful fuzzy match: higher [score] is better. */
data class FuzzyResult(val score: Int)

/** Subsequence match with bonuses for word starts and consecutive runs; null when [query] does not fit [candidate]. */
fun fuzzyMatch(query: String, candidate: String): FuzzyResult? {
    val needle = query.trim().lowercase().replace(" ", "")
    if (needle.isEmpty()) return FuzzyResult(0)
    val haystack = candidate.lowercase()
    val positions = mutableListOf<Int>()
    var score = 0
    var from = 0
    for (char in needle) {
        val found = haystack.indexOf(char, from)
        if (found < 0) return null
        score += hitScore(haystack, found, positions.lastOrNull())
        positions += found
        from = found + 1
    }
    return FuzzyResult(score - positions.first() * GAP_PENALTY)
}

private fun hitScore(haystack: String, index: Int, previous: Int?): Int {
    var score = BASE_HIT
    if (index == 0) score += FIRST_CHAR_BONUS
    if (index > 0 && !haystack[index - 1].isLetterOrDigit()) score += WORD_START_BONUS
    if (previous != null && previous == index - 1) score += CONSECUTIVE_BONUS
    if (previous != null && previous < index - 1) score -= (index - previous - 1) * GAP_PENALTY
    return score
}

/** Items matching [query], best first; ties keep their original order. */
fun <T> fuzzyFilter(query: String, items: List<T>, text: (T) -> String): List<T> =
    items.mapNotNull { item -> fuzzyMatch(query, text(item))?.let { item to it.score } }
        .sortedByDescending { it.second }
        .map { it.first }
