package com.github.krpkbhdn.ccmanager.ui.git.log

import com.github.krpkbhdn.ccmanager.git.GitLog
import com.github.krpkbhdn.ccmanager.ui.git.FakeGit
import com.github.krpkbhdn.ccmanager.ui.git.FakeRepoData
import com.github.krpkbhdn.ccmanager.ui.git.model.GitDialog
import com.github.krpkbhdn.ccmanager.ui.git.model.GitHost
import com.github.krpkbhdn.ccmanager.ui.git.model.GitPrefs
import com.github.krpkbhdn.ccmanager.ui.git.model.GitState
import java.nio.file.Path
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class CommitMenuTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val data = FakeRepoData()
    private val git = GitState(1, Path.of("repo"), FakeGit(data).backend, scope, GitHost({ GitPrefs() }, { }, { _, _, _ -> }))
    private val commit = GitLog.Commit("a".repeat(40), "aaaaaaa", listOf(), "Ann", "a@x", Instant.EPOCH, "Fix it", "", listOf())

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `cherry-pick and revert ask before touching the branch`() {
        for (label in listOf("Cherry-pick", "Revert")) {
            commitMenu(git, commit).first { it.label == label }.onSelect()

            val dialog = git.dialog
            assertThat(dialog).isInstanceOf(GitDialog.Confirm::class.java)
            assertThat((dialog as GitDialog.Confirm).request.confirmLabel).isEqualTo(label)
            assertThat(data.calls).isEmpty()
            git.dialog = null
        }
    }
}
