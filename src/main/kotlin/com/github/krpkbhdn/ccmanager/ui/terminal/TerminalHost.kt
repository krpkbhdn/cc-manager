package com.github.krpkbhdn.ccmanager.ui.terminal

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession

/** Owns one terminal widget per session so tabs survive navigation. */
object TerminalHost {
    fun open(session: LaunchedSession): Unit = TODO()

    fun isOpen(ccmSessionId: String): Boolean = TODO()

    /** Types text into the session console exactly as if the user typed it (CR = Enter, ESC = Esc). */
    fun send(ccmSessionId: String, text: String): Unit = TODO()

    /** Disposes the widget and kills the process if still running. */
    fun close(ccmSessionId: String): Unit = TODO()
}
