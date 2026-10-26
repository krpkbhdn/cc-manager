package com.github.krpkbhdn.ccmanager.ui.git.model

import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CommitDraftTest {
    private val draft = CommitDraft()

    @Test
    fun `amending loads the last message and switching it off restores what was typed`() {
        draft.message.setTextAndPlaceCursorAtEnd("wip text")

        draft.setAmend(true, "Last subject")
        assertThat(draft.message.text.toString()).isEqualTo("Last subject")

        draft.setAmend(false, null)
        assertThat(draft.message.text.toString()).isEqualTo("wip text")
        assertThat(draft.amend).isFalse()
    }

    @Test
    fun `amending without a readable last commit keeps the typed text`() {
        draft.message.setTextAndPlaceCursorAtEnd("wip text")

        draft.setAmend(true, null)

        assertThat(draft.amend).isTrue()
        assertThat(draft.message.text.toString()).isEqualTo("wip text")
    }

    @Test
    fun `clearing empties the message and leaves amend mode`() {
        draft.setAmend(true, "Last subject")

        draft.clear()

        assertThat(draft.message.text).isEmpty()
        assertThat(draft.amend).isFalse()
    }

    @Test
    fun `a commit leaves amend mode even when the message was edited meanwhile`() {
        draft.setAmend(true, "Last subject")
        draft.message.setTextAndPlaceCursorAtEnd("edited")

        draft.committed("Last subject")

        assertThat(draft.message.text.toString()).isEqualTo("edited")
        assertThat(draft.amend).isFalse()
    }
}
