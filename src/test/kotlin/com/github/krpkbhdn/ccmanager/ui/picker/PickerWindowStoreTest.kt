package com.github.krpkbhdn.ccmanager.ui.picker

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.awt.Rectangle
import java.nio.file.Files
import java.nio.file.Path

class PickerWindowStoreTest {
    @TempDir
    lateinit var dir: Path

    private val screen = Rectangle(0, 0, 1920, 1080)
    private fun store() = PickerWindowStore(dir.resolve("data").resolve("picker-window.txt"))

    @Test
    fun startsEmptyWhenNothingSaved() {
        assertThat(store().load()).isNull()
    }

    @Test
    fun savesAndReloadsBoundsAcrossInstances() {
        store().save(PickerBounds(-30, 40, 900, 650))

        assertThat(store().load()).isEqualTo(PickerBounds(-30, 40, 900, 650))
    }

    @Test
    fun ignoresCorruptFile() {
        Files.createDirectories(dir.resolve("data"))
        Files.writeString(dir.resolve("data").resolve("picker-window.txt"), "garbage 1 2")

        assertThat(store().load()).isNull()
    }

    @Test
    fun clampsTinyAndHugeSizesAndPullsWindowBackOnScreen() {
        val clamped = PickerWindowStore.clamp(PickerBounds(5000, -200, 100, 99999), listOf(screen))

        assertThat(clamped.width).isEqualTo(PickerWindowStore.MIN_WIDTH)
        assertThat(clamped.height).isEqualTo(1080)
        assertThat(clamped.x).isLessThan(1920)
        assertThat(clamped.y).isEqualTo(0)
    }

    @Test
    fun keepsPositionOnASecondMonitor() {
        val second = Rectangle(1920, 0, 1920, 1080)

        val clamped = PickerWindowStore.clamp(PickerBounds(2500, 100, 860, 600), listOf(screen, second))

        assertThat(clamped).isEqualTo(PickerBounds(2500, 100, 860, 600))
    }
}
