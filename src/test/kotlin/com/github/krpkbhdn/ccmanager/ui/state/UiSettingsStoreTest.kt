package com.github.krpkbhdn.ccmanager.ui.state

import java.nio.file.Files
import java.nio.file.Path
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class UiSettingsStoreTest {
    @Test
    fun `missing file yields defaults`(@TempDir dir: Path) {
        assertThat(UiSettingsStore(dir.resolve("none.properties")).load()).isEqualTo(UiSettings())
    }

    @Test
    fun `saved settings round trip`(@TempDir dir: Path) {
        val store = UiSettingsStore(dir.resolve("nested/ui.properties"))
        val settings = UiSettings(ThemeMode.LIGHT, sidebarCollapsed = true, agentsOpen = false, quickBarOpen = false, window = WindowBounds(1500, 900, 40, 60))
        store.save(settings)
        assertThat(store.load()).isEqualTo(settings)
    }

    @Test
    fun `window position is optional`(@TempDir dir: Path) {
        val store = UiSettingsStore(dir.resolve("ui.properties"))
        store.save(UiSettings(window = WindowBounds(1000, 700)))
        assertThat(store.load().window).isEqualTo(WindowBounds(1000, 700, null, null))
    }

    @Test
    fun `garbage values fall back field by field`(@TempDir dir: Path) {
        val file = dir.resolve("ui.properties")
        Files.writeString(file, "theme=NEON\nsidebar.collapsed=maybe\nagents.open=false\nwindow.width=wide\n")
        val loaded = UiSettingsStore(file).load()
        assertThat(loaded.themeMode).isEqualTo(ThemeMode.SYSTEM)
        assertThat(loaded.sidebarCollapsed).isFalse()
        assertThat(loaded.agentsOpen).isFalse()
        assertThat(loaded.window.width).isEqualTo(UiSettings.DEFAULT_WIDTH)
    }

    @Test
    fun `an in memory store never touches the disk`() {
        val store = UiSettingsStore(null)
        store.save(UiSettings(themeMode = ThemeMode.DARK))
        assertThat(store.load()).isEqualTo(UiSettings())
    }
}
