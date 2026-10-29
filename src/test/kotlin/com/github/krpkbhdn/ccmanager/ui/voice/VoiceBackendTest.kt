package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
import com.github.krpkbhdn.ccmanager.voice.VoiceInputService
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService
import com.github.krpkbhdn.ccmanager.voice.audio.Microphone
import com.github.krpkbhdn.ccmanager.voice.device.DeviceService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock

class VoiceBackendTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @AfterEach
    fun stop() = scope.cancel()

    @Test
    fun `without voice services there is no settings state`() {
        assertThat(voiceSettingsOf(null, scope, { UiSettings() }, {}, { _, _ -> })).isNull()
    }

    @Test
    fun `with voice services the settings state reads the saved settings`() {
        val backend = VoiceBackend(mock(VoiceInputService::class.java), mock(DeviceService::class.java), mock(VoiceAssetService::class.java), mock(Microphone::class.java))

        val state = voiceSettingsOf(backend, scope, { UiSettings(voiceEnabled = true) }, {}, { _, _ -> })

        assertThat(state).isNotNull()
        assertThat(state!!.enabled).isTrue()
    }
}
