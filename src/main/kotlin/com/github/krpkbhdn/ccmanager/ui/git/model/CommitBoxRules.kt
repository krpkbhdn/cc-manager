package com.github.krpkbhdn.ccmanager.ui.git.model

const val SUBJECT_SOFT_LIMIT = 50
const val SUBJECT_HARD_LIMIT = 72

enum class SubjectLevel { OK, LONG, TOO_LONG }

/** Length of the first line and how it compares with the 50 / 72 conventions. */
data class SubjectHint(val length: Int, val level: SubjectLevel)

fun subjectOf(message: String): String = message.lineSequence().firstOrNull().orEmpty()

fun subjectHint(message: String): SubjectHint {
    val length = subjectOf(message).trim().length
    val level = when {
        length > SUBJECT_HARD_LIMIT -> SubjectLevel.TOO_LONG
        length > SUBJECT_SOFT_LIMIT -> SubjectLevel.LONG
        else -> SubjectLevel.OK
    }
    return SubjectHint(length, level)
}

/** Commit needs a message and something to commit; an amend may reuse the previous content and only change the message. */
fun canCommit(message: String, stagedCount: Int, amend: Boolean, busy: Boolean, conflicts: Int = 0): Boolean =
    !busy && conflicts == 0 && message.isNotBlank() && (stagedCount > 0 || amend)

/** Text to restore when Amend is unchecked: what the user had typed before the box was prefilled. */
data class AmendMemory(val typedBefore: String = "") {
    fun onToggle(amend: Boolean, current: String, lastMessage: String): Pair<String, AmendMemory> =
        if (amend) lastMessage to AmendMemory(current) else typedBefore to AmendMemory()
}
