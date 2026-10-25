package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.claude.hook.HookEventType
import com.github.krpkbhdn.ccmanager.workspace.GitDiff
import java.time.Instant
import java.time.ZoneOffset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ActivityFormatsTest {
    private fun file(path: String, change: GitDiff.ChangeType = GitDiff.ChangeType.MODIFIED, oldPath: String? = null) =
        GitDiff.FileDiff(path, oldPath, change, 1, 1, false, emptyList())

    @Test
    fun `event types are toned by urgency`() {
        assertThat(activityTone(HookEventType.PERMISSION_REQUEST)).isEqualTo(ActivityTone.DANGER)
        assertThat(activityTone(HookEventType.STOP)).isEqualTo(ActivityTone.SUCCESS)
        assertThat(activityTone(HookEventType.USER_PROMPT_SUBMIT)).isEqualTo(ActivityTone.ACCENT)
        assertThat(activityTone(HookEventType.PRE_TOOL_USE)).isEqualTo(ActivityTone.NEUTRAL)
    }

    @Test
    fun `event label is humanised`() {
        assertThat(eventLabel(HookEventType.PRE_TOOL_USE)).isEqualTo("pre tool use")
    }

    @Test
    fun `clock is formatted in the given zone`() {
        assertThat(formatClock(Instant.parse("2026-01-01T12:34:56Z"), ZoneOffset.UTC)).isEqualTo("12:34:56")
    }

    @Test
    fun `diff lines get markers and blank gutters for missing numbers`() {
        assertThat(lineMarker(GitDiff.LineKind.ADDED)).isEqualTo("+")
        assertThat(lineMarker(GitDiff.LineKind.REMOVED)).isEqualTo("-")
        assertThat(lineMarker(GitDiff.LineKind.CONTEXT)).isEqualTo(" ")
        assertThat(lineNumberText(7)).isEqualTo("7")
        assertThat(lineNumberText(null)).isEmpty()
    }

    @Test
    fun `renamed files show both names`() {
        assertThat(renameLabel(file("new", GitDiff.ChangeType.RENAMED, "old"))).isEqualTo("old -> new")
        assertThat(renameLabel(file("same"))).isEqualTo("same")
    }

    @Test
    fun `quick actions type real control sequences`() {
        val byLabel = quickActions.associateBy { it.label }

        assertThat(byLabel.getValue("Interrupt").keys).isEqualTo("\u001b")
        assertThat(byLabel.getValue("Plan mode").keys).isEqualTo("\u001b[Z")
        assertThat(byLabel.getValue("Compact").keys).isEqualTo("/compact\r")
        assertThat(quickActions).hasSize(8)
    }
}
