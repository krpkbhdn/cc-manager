package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorKind
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthorLabelsTest {
    @Test
    fun onlyAnEntryThatReplacesAnExistingTeamWarnsAboutLostEdits() {
        val team = authorItem("team", kind = AuthorKind.TEAM)

        assertThat(AuthorStatus.entries.map { replacesEdits(authorEntry(team, it, replaces = true)) }).containsOnly(true)
        assertThat(replacesEdits(authorEntry(team))).isFalse()
    }

    @Test
    fun statusLabelsAreReadable() {
        assertThat(AuthorStatus.entries.map(::statusLabel)).containsExactly("New", "Installed", "Update", "Declined")
    }
}
