package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
import com.github.krpkbhdn.ccmanager.voice.VoiceInputService
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService
import com.github.krpkbhdn.ccmanager.voice.audio.Microphone
import com.github.krpkbhdn.ccmanager.voice.device.DeviceService
import kotlinx.coroutines.CoroutineScope

/** The Spring services behind the voice settings page; the whole set is present or voice input is unavailable. */
data class VoiceBackend(val input: VoiceInputService, val devices: DeviceService, val assets: VoiceAssetService, val microphone: Microphone)

fun voiceSettingsOf(
    backend: VoiceBackend?,
    scope: CoroutineScope,
    settings: () -> UiSettings,
    update: ((UiSettings) -> UiSettings) -> Unit,
    reportError: (title: String, message: String) -> Unit,
): VoiceSettingsState? = backend?.let { VoiceSettingsState(it, scope, settings, update, reportError) }
