package com.github.krpkbhdn.ccmanager.ui.state

import com.github.krpkbhdn.ccmanager.ui.assistant.AssistantStartup
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
        val settings = UiSettings(ThemeMode.LIGHT, sidebarCollapsed = true, agentsOpen = false, quickBarOpen = false, window = WindowBounds(1500, 900, 40, 60, maximized = true))
        store.save(settings)
        assertThat(store.load()).isEqualTo(settings)
    }

    @Test
    fun `assistant startup mode round trips and garbage falls back to ask`(@TempDir dir: Path) {
        val file = dir.resolve("ui.properties")
        val store = UiSettingsStore(file)
        store.save(UiSettings(assistantStartup = AssistantStartup.AUTO_START))
        assertThat(store.load().assistantStartup).isEqualTo(AssistantStartup.AUTO_START)
        Files.writeString(file, "assistant.startup=SOMETIMES\n")
        assertThat(store.load().assistantStartup).isEqualTo(AssistantStartup.ASK)
    }

    @Test
    fun `system notification settings default sensibly and round trip`(@TempDir dir: Path) {
        val store = UiSettingsStore(dir.resolve("ui.properties"))
        assertThat(store.load()).extracting({ it.osNotifyNeedsAction }, { it.osNotifyFinished }, { it.osNotifyError }, { it.doNotDisturb }, { it.minimizeToTray })
            .containsExactly(true, true, true, false, true)
        val settings = UiSettings(osNotifyNeedsAction = false, osNotifyFinished = false, osNotifyError = false, doNotDisturb = true, minimizeToTray = false)
        store.save(settings)
        assertThat(store.load()).isEqualTo(settings)
    }

    @Test
    fun `file without the notification keys reads with defaults`(@TempDir dir: Path) {
        val file = dir.resolve("ui.properties")
        java.nio.file.Files.writeString(file, listOf("theme=DARK", "sidebar.collapsed=true").joinToString(System.lineSeparator()))
        val loaded = UiSettingsStore(file).load()
        assertThat(loaded.themeMode).isEqualTo(ThemeMode.DARK)
        assertThat(loaded).extracting({ it.osNotifyNeedsAction }, { it.osNotifyFinished }, { it.osNotifyError }, { it.doNotDisturb }, { it.minimizeToTray }, { it.notificationSound })
            .containsExactly(true, true, true, false, true, true)
    }

    @Test
    fun `a finished flag saved under the old key does not override the new default`(@TempDir dir: Path) {
        val file = dir.resolve("ui.properties")
        Files.writeString(file, "notification.os.finished=false\n")
        assertThat(UiSettingsStore(file).load().osNotifyFinished).isTrue()
    }

    @Test
    fun `voice settings default to off and round trip`(@TempDir dir: Path) {
        val store = UiSettingsStore(dir.resolve("ui.properties"))
        assertThat(store.load()).extracting({ it.voiceEnabled }, { it.voiceModel }, { it.voiceDevice }, { it.voiceInput }).containsExactly(false, "base-q5_1", "best", "")
        val settings = UiSettings(voiceEnabled = true, voiceModel = "large-v3-turbo-q5_0", voiceDevice = "NVIDIA GeForce RTX 5080", voiceInput = "USB mic")
        store.save(settings)
        assertThat(store.load()).isEqualTo(settings)
    }

    @Test
    fun `blank or garbage voice values do not break loading`(@TempDir dir: Path) {
        val file = dir.resolve("ui.properties")
        Files.writeString(file, "voice.enabled=yes\nvoice.model=\nvoice.device=\nvoice.input=\n")
        val loaded = UiSettingsStore(file).load()
        assertThat(loaded.voiceEnabled).isFalse()
        assertThat(loaded.voiceModel).isEmpty()
        assertThat(loaded.voiceDevice).isEmpty()
        assertThat(loaded.voiceInput).isEmpty()
        Files.writeString(file, "voice.model=gigantic\nvoice.device=Gone GPU\n")
        assertThat(UiSettingsStore(file).load()).extracting({ it.voiceModel }, { it.voiceDevice }).containsExactly("gigantic", "Gone GPU")
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
