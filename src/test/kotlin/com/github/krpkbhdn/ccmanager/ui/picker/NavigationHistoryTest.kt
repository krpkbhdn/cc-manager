package com.github.krpkbhdn.ccmanager.ui.picker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path

class NavigationHistoryTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun visitingKeepsWholePathsAndEnablesBack() {
        val deep = root.resolve("a").resolve("b")

        val history = NavigationHistory(root).visit(deep)

        assertThat(history.current).isEqualTo(deep)
        assertThat(history.canGoBack).isTrue()
        assertThat(history.canGoForward).isFalse()
    }

    @Test
    fun backThenForwardReturnsToSamePlace() {
        val history = NavigationHistory(root).visit(root.resolve("a")).visit(root.resolve("b"))

        assertThat(history.back().current).isEqualTo(root.resolve("a"))
        assertThat(history.back().back().canGoBack).isFalse()
        assertThat(history.back().forward().current).isEqualTo(root.resolve("b"))
    }

    @Test
    fun visitingAfterBackDropsForwardEntries() {
        val history = NavigationHistory(root).visit(root.resolve("a")).back().visit(root.resolve("c"))

        assertThat(history.canGoForward).isFalse()
        assertThat(history.back().current).isEqualTo(root)
    }

    @Test
    fun revisitingCurrentFolderDoesNotGrowHistory() {
        val history = NavigationHistory(root).visit(root)

        assertThat(history.canGoBack).isFalse()
    }

    @Test
    fun upGoesToParentAndStopsAtRoot() {
        val history = NavigationHistory(root.resolve("a")).up()

        assertThat(history.current).isEqualTo(root)
        assertThat(NavigationHistory(root.root).canGoUp).isFalse()
        assertThat(NavigationHistory(root.root).up().current).isEqualTo(root.root)
    }
}
