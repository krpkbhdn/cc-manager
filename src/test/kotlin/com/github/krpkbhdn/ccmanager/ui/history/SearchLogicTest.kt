package com.github.krpkbhdn.ccmanager.ui.history

import com.github.krpkbhdn.ccmanager.project.Project
import java.nio.file.Path
import java.time.Instant
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class SearchLogicTest {
    private fun project(id: Long, path: String, group: Boolean = false) = Project(id, "p$id", Path.of(path), Instant.EPOCH, com.github.krpkbhdn.ccmanager.project.ModelSelection.DEFAULT, group, null)

    @Test
    fun snippetIsSplitAtMatchMarkers() {
        val parts = parseSnippet("...fix the \u0002login\u0003 bug \u0002now\u0003")

        assertThat(parts).containsExactly(SnippetPart("...fix the ", false), SnippetPart("login", true), SnippetPart(" bug ", false), SnippetPart("now", true))
    }

    @Test
    fun snippetWithoutMarkersIsOnePlainPart() {
        assertThat(parseSnippet("plain")).containsExactly(SnippetPart("plain", false))
        assertThat(parseSnippet("")).isEmpty()
    }

    @Test
    fun unclosedMatchRunsToTheEnd() {
        assertThat(parseSnippet("a \u0002b")).containsExactly(SnippetPart("a ", false), SnippetPart("b", true))
    }

    @Test
    fun plainSnippetDropsMarkers() {
        assertThat(plainSnippet("a \u0002b\u0003 c")).isEqualTo("a b c")
    }

    @Test
    fun periodCountsBackFromNow() {
        val now = Instant.ofEpochSecond(1_000_000)

        assertThat(SearchPeriod.ANY.from(now)).isNull()
        assertThat(SearchPeriod.DAY.from(now)).isEqualTo(now.minusSeconds(86_400))
        assertThat(SearchPeriod.WEEK.from(now)).isEqualTo(now.minusSeconds(7 * 86_400))
    }

    @Test
    fun cwdBelongsToTheDeepestContainingProject() {
        val outer = project(1, "/work/app")
        val inner = project(2, "/work/app/service")

        assertThat(projectOfCwd("/work/app/service/src", listOf(outer, inner))).isEqualTo(inner)
        assertThat(projectOfCwd("/work/app", listOf(outer, inner))).isEqualTo(outer)
    }

    @Test
    fun siblingWithSharedPrefixAndGroupsDoNotMatch() {
        assertThat(projectOfCwd("/work/app2", listOf(project(1, "/work/app")))).isNull()
        assertThat(projectOfCwd("/work/app", listOf(project(1, "/work/app", group = true)))).isNull()
        assertThat(projectOfCwd("\u0000bad", listOf(project(1, "/work/app")))).isNull()
    }
}
