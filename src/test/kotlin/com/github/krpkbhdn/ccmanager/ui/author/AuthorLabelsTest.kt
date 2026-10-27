package com.github.krpkbhdn.ccmanager.ui.author

import com.github.krpkbhdn.ccmanager.author.AuthorComponent
import com.github.krpkbhdn.ccmanager.author.AuthorEntry
import com.github.krpkbhdn.ccmanager.author.AuthorStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AuthorLabelsTest {
    private fun entry(status: AuthorStatus) = AuthorEntry(AuthorComponent.USER_SETUP, 0, "User", "summary", 3, 2, status, emptyList(), false)

    @Test
    fun onlyTeamsThatReplaceAnExistingTeamWarnAboutLostEdits() {
        fun teams(status: AuthorStatus, replaces: Boolean) = AuthorEntry(AuthorComponent.TEAMS, 0, "Teams", "s", 1, 0, status, emptyList(), replaces)

        assertThat(AuthorStatus.entries.map { replacesEdits(teams(it, true)) }).containsOnly(true)
        assertThat(replacesEdits(teams(AuthorStatus.NOT_INSTALLED, false))).isFalse()
        assertThat(replacesEdits(AuthorEntry(AuthorComponent.USER_SETUP, 0, "U", "s", 1, 0, AuthorStatus.INSTALLED, emptyList(), true))).isFalse()
    }

    @Test
    fun buttonsAlwaysAllowInstallingAndSayUpdateOrReinstallWhenItApplies() {
        assertThat(AuthorStatus.entries.map(::buttonLabel)).containsExactly("Install", "Reinstall", "Update", "Install")
    }

    @Test
    fun statusLabelsAreReadable() {
        assertThat(statusLabel(AuthorStatus.UPDATE_AVAILABLE)).isEqualTo("Update available")
        assertThat(statusLabel(AuthorStatus.DECLINED)).isEqualTo("Declined")
    }

    @Test
    fun versionTagMarksNewComponentsAndNamesTheUpdateTarget() {
        assertThat(versionTag(entry(AuthorStatus.NOT_INSTALLED))).isEqualTo("New")
        assertThat(versionTag(entry(AuthorStatus.UPDATE_AVAILABLE))).isEqualTo("Update to v3")
        assertThat(versionTag(entry(AuthorStatus.INSTALLED))).isEqualTo("v2")
    }
}
