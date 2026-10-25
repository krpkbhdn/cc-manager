package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.session.AgentStatus
import java.nio.file.Path
import java.time.Duration
import com.github.krpkbhdn.ccmanager.claude.session.SessionSnapshot
import java.time.Instant
import java.time.ZoneOffset
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

    private fun session(title: String?) =
        SessionSnapshot("s1", 1, null, AgentStatus.IDLE, null, null, null, title, Instant.parse("2026-01-01T12:34:00Z"), Instant.EPOCH, emptyList(), 0, null)

    @Test
    fun `session without a topic is named by its start time`() {
        assertThat(sessionName(session(null), ZoneOffset.UTC)).isEqualTo("Session · 12:34")
        assertThat(sessionTitle(session("  "))).startsWith("Session · ")
    }

    @Test
    fun `short topics stay and long topics are cut on a word boundary`() {
        assertThat(sessionTitle(session("Fix login"))).isEqualTo("Fix login")
        assertThat(sessionTitle(session("Refactor the session registry to read titles"))).isEqualTo("Refactor the session…")
        assertThat(sessionTitle(session("A".repeat(40)))).isEqualTo("A".repeat(28) + "…")
    }
}
