package com.github.krpkbhdn.ccmanager.ui.voice

/** What the dictation status row under a field shows. */
sealed interface DictationStatus {
    data object Idle : DictationStatus

    data object Initializing : DictationStatus

    data object Listening : DictationStatus

    data class Failed(val message: String) : DictationStatus
}
