package com.github.krpkbhdn.ccmanager.ui.git.model

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.git.GitMisc.CommitRequest

/** The commit box's content; lives in [GitState] so a half-written message survives switching tabs. */
class CommitDraft {
    val message = TextFieldState()
    var amend by mutableStateOf(false)
        private set
    var signOff by mutableStateOf(false)
    var skipHooks by mutableStateOf(false)
    private var memory = AmendMemory()

    /** Turning amend on loads [lastMessage]; turning it off puts back what was typed before. */
    fun setAmend(on: Boolean, lastMessage: String?) {
        if (on == amend) return
        val (text, next) = memory.onToggle(on, message.text.toString(), lastMessage.orEmpty())
        memory = next
        amend = on
        if (on && lastMessage == null) return
        message.setTextAndPlaceCursorAtEnd(text)
    }

    fun request(): CommitRequest = CommitRequest(message.text.toString().trim(), amend, signOff, skipHooks)

    fun clear() {
        message.clearText()
        amend = false
        memory = AmendMemory()
    }
}
