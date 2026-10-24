package com.github.krpkbhdn.ccmanager.ui.picker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class PlacesTest {
    @TempDir
    lateinit var home: Path

    @Test
    fun listsHomeAndOnlyTheStandardAndWorkFoldersThatExist() {
        Files.createDirectories(home.resolve("Documents"))
        Files.createDirectories(home.resolve("Projects"))

        val labels = Places.quick(home).map { it.label }

        assertThat(labels).containsExactly("Home", "Documents", "Projects")
    }

    @Test
    fun recentSkipsFoldersThatNoLongerExist() {
        val alive = Files.createDirectories(home.resolve("alive"))

        val recent = Places.recent(listOf(home.resolve("gone"), alive))

        assertThat(recent.map { it.path }).containsExactly(alive)
        assertThat(recent.single().label).isEqualTo("alive")
    }

    @Test
    fun drivesContainEveryExistingFileSystemRoot() {
        assertThat(Places.drives()).isNotEmpty()
    }

    @Test
    fun formatsByteCountsWithBinaryUnits() {
        assertThat(Places.formatBytes(512)).isEqualTo("512 B")
        assertThat(Places.formatBytes(1536)).isEqualTo("1.5 KB")
        assertThat(Places.formatBytes(250L * 1024 * 1024 * 1024)).isEqualTo("250 GB")
    }
}
