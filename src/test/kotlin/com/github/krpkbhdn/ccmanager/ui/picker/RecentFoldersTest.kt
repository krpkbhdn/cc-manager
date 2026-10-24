package com.github.krpkbhdn.ccmanager.ui.picker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class RecentFoldersTest {
    @TempDir
    lateinit var dir: Path

    private fun recents(limit: Int = RecentFolders.DEFAULT_LIMIT) = RecentFolders(dir.resolve("data").resolve("recent.txt"), limit)

    @Test
    fun startsEmptyWhenNothingSaved() {
        assertThat(recents().load()).isEmpty()
    }

    @Test
    fun keepsMostRecentFirstAndPersistsAcrossInstances() {
        recents().apply { add(dir.resolve("a")); add(dir.resolve("b")) }

        assertThat(recents().load()).containsExactly(dir.resolve("b"), dir.resolve("a"))
    }

    @Test
    fun movesRepeatedChoiceToTheFrontWithoutDuplicates() {
        val store = recents().apply { add(dir.resolve("a")); add(dir.resolve("b")); add(dir.resolve("a")) }

        assertThat(store.load()).containsExactly(dir.resolve("a"), dir.resolve("b"))
    }

    @Test
    fun dropsEntriesBeyondTheLimit() {
        val store = recents(limit = 3).apply { (1..5).forEach { add(dir.resolve("f$it")) } }

        assertThat(store.load()).containsExactly(dir.resolve("f5"), dir.resolve("f4"), dir.resolve("f3"))
    }

    @Test
    fun ignoresBlankLinesInAHandEditedFile() {
        val file = dir.resolve("data").resolve("recent.txt")
        Files.createDirectories(file.parent)
        Files.writeString(file, "\n  \n${dir.resolve("kept")}\n")

        assertThat(recents().load()).containsExactly(dir.resolve("kept"))
    }

    @Test
    fun savingToAnUnwritableLocationDoesNotThrow() {
        val blocker = Files.createFile(dir.resolve("blocker"))

        RecentFolders(blocker.resolve("recent.txt")).add(dir)
    }
}
