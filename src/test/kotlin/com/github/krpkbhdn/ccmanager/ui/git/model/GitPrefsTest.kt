package com.github.krpkbhdn.ccmanager.ui.git.model

import com.github.krpkbhdn.ccmanager.git.GitMisc.PullMode
import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
import com.github.krpkbhdn.ccmanager.ui.state.UiSettingsStore
import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class GitPrefsTest {
    @Test
    fun `git preferences survive a restart`(@TempDir dir: Path) {
        val store = UiSettingsStore(dir.resolve("ui.properties"))
        val prefs = GitPrefs().withView(7, GitSubView.LOG).withView(9, GitSubView.STASHES).copy(pullMode = PullMode.REBASE, flatChanges = true)

        store.save(UiSettings(git = prefs))

        assertThat(store.load().git).isEqualTo(prefs)
    }

    @Test
    fun `unknown projects start on changes and garbage is ignored`(@TempDir dir: Path) {
        val file = dir.resolve("ui.properties")
        Files.writeString(file, "git.view.5=LOG\ngit.view.x=LOG\ngit.view.6=NOPE\ngit.pull=SIDEWAYS\n")

        val prefs = UiSettingsStore(file).load().git

        assertThat(prefs.viewOf(5)).isEqualTo(GitSubView.LOG)
        assertThat(prefs.viewOf(6)).isEqualTo(GitSubView.CHANGES)
        assertThat(prefs.pullMode).isEqualTo(PullMode.MERGE)
    }
}
