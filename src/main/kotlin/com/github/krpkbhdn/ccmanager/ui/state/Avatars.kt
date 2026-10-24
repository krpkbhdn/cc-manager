package com.github.krpkbhdn.ccmanager.ui.state

private const val MAX_INITIALS = 2

/** Curated gradient pairs (top, bottom) as ARGB; the same project name always lands on the same pair. */
val avatarGradients: List<Pair<Long, Long>> = listOf(
    0xFFE98A63 to 0xFFC4553A,
    0xFF6FA8F5 to 0xFF3E6FD1,
    0xFF55C79B to 0xFF2A9170,
    0xFFB58CF2 to 0xFF7B53C9,
    0xFFF2B65A to 0xFFC98419,
    0xFFEF7FA6 to 0xFFC24A78,
    0xFF52C2D6 to 0xFF2B8DA6,
    0xFF9CB85A to 0xFF6C8A2D,
    0xFFE77E7E to 0xFFBA4B4B,
    0xFF8A92F2 to 0xFF5560CE,
)

/** Stable index into [avatarGradients]; independent of case and surrounding spaces. */
fun avatarIndex(name: String): Int {
    val hash = name.trim().lowercase().fold(0) { acc, char -> acc * HASH_FACTOR + char.code }
    return Math.floorMod(hash, avatarGradients.size)
}

fun avatarGradient(name: String): Pair<Long, Long> = avatarGradients[avatarIndex(name)]

/** One or two letters for the avatar: first letters of the first two words, or the first two letters of a single word. */
fun avatarInitials(name: String): String {
    val words = name.trim().split(Regex("[\\s_\\-./]+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words.first().take(MAX_INITIALS).let { it.take(1).uppercase() + it.drop(1).lowercase() }
        else -> words.take(MAX_INITIALS).joinToString("") { it.take(1).uppercase() }
    }
}

private const val HASH_FACTOR = 31
