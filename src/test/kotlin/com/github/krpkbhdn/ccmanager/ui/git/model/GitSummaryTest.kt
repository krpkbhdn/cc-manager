package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitStatus
import com.github.krpkbhdn.ccmanager.git.GitStatus.Kind
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitSummaryTest {
    private fun status(branch: String?, ahead: Int = 0, behind: Int = 0, vararg changes: GitStatus.Change) =
        GitStatus(true, branch, "0123456789abcdef", branch == null, "origin/main", ahead, behind, GitStatus.Operation.NONE, 0, changes.toList())

    @Test
    fun `a repository summarises branch dirty count and divergence`() {
        val summary = summaryOf(status("main", 2, 1, GitStatus.Change("a", null, Kind.MODIFIED, Kind.MODIFIED), GitStatus.Change("b", null, Kind.NONE, Kind.UNTRACKED)))!!

        assertThat(summary).isEqualTo(GitSummary("main", 2, 2, 1, GitStatus.Operation.NONE))
        assertThat(summaryText(summary)).isEqualTo("main  2 changed  ↑2  ↓1")
    }

    @Test
    fun `a detached head shows the short hash`() {
        assertThat(summaryOf(status(null))!!.branch).isEqualTo("detached 0123456")
    }

    @Test
    fun `no repository means no summary`() {
        assertThat(summaryOf(GitStatus.notARepository())).isNull()
    }

    @Test
    fun `a clean branch shows only its name`() {
        assertThat(summaryText(summaryOf(status("dev"))!!)).isEqualTo("dev")
    }
}
