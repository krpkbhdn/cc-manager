package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FormatsTest {
    private val now = Instant.parse("2026-01-01T12:00:00Z")

    @Test
    fun `elapsed time switches unit at minute and hour boundaries`() {
        assertThat(formatElapsed(Duration.ofSeconds(42))).isEqualTo("42s")
        assertThat(formatElapsed(Duration.ofSeconds(245))).isEqualTo("4m 05s")
        assertThat(formatElapsed(Duration.ofMinutes(130))).isEqualTo("2h 10m")
        assertThat(formatElapsed(Duration.ofSeconds(-5))).isEqualTo("0s")
    }

    @Test
    fun `relative time is coarse and monotonic`() {
        assertThat(relativeTime(now.minusSeconds(3), now)).isEqualTo("just now")
        assertThat(relativeTime(now.minusSeconds(40), now)).isEqualTo("40s ago")
        assertThat(relativeTime(now.minusSeconds(300), now)).isEqualTo("5m ago")
        assertThat(relativeTime(now.minusSeconds(7200), now)).isEqualTo("2h ago")
        assertThat(relativeTime(now.minusSeconds(3 * 86_400L), now)).isEqualTo("3d ago")
    }

    @Test
    fun `every status has a distinct label`() {
        assertThat(AgentStatus.entries.map(::statusLabel)).doesNotHaveDuplicates().contains("Free", "Needs action")
    }

    @Test
    fun `counts are pluralised`() {
        assertThat(sessionCountLabel(0)).isEqualTo("No sessions")
        assertThat(sessionCountLabel(1)).isEqualTo("1 session")
        assertThat(sessionCountLabel(3)).isEqualTo("3 sessions")
        assertThat(backgroundTasksLabel(1)).isEqualTo("1 background task")
        assertThat(backgroundTasksLabel(2)).isEqualTo("2 background tasks")
    }

    @Test
    fun `path tail keeps the last segments`() {
        assertThat(pathTail(Path.of("/home/dev/Work/cc-manager"))).isEqualTo("Work/cc-manager")
        assertThat(pathTail(Path.of("/home/dev/Work/cc-manager"), 1)).isEqualTo("cc-manager")
    }

    @Test
    fun `permission modes get readable labels and unknown modes pass through`() {
        assertThat(permissionModeLabel("acceptEdits")).isEqualTo("Accept edits")
        assertThat(permissionModeLabel("custom")).isEqualTo("custom")
        assertThat(permissionModeLabel(null)).isNull()
    }

    @Test
    fun `change types map to git letters`() {
        assertThat(GitDiff.ChangeType.entries.map(::changeLetter)).containsExactly("A", "M", "D", "R", "U")
    }

    @Test
    fun `file path is split into name and directory`() {
        assertThat(fileName("src/main/App.java")).isEqualTo("App.java")
        assertThat(parentDirectory("src/main/App.java")).isEqualTo("src/main")
        assertThat(parentDirectory("README.md")).isEmpty()
    }

    @Test
    fun `install commands list the current platform first`() {
        assertThat(installCommands("Windows 11").first().second).contains("install.ps1")
        assertThat(installCommands("Linux").first().second).contains("install.sh")
    }
}
