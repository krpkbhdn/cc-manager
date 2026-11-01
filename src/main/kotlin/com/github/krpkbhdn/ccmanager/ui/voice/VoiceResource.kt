package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset
import java.util.Locale

sealed interface VoiceResourceStatus {
    data object Installed : VoiceResourceStatus
    data object Missing : VoiceResourceStatus
    data class Downloading(val fraction: Float) : VoiceResourceStatus
    data class Failed(val message: String) : VoiceResourceStatus
}

/** One download the voice input needs: the whisper runtime of the chosen device, the voice activity model or the chosen whisper model. */
data class VoiceResource(val asset: VoiceAsset, val status: VoiceResourceStatus) {
    val name: String get() = asset.name()
    val sizeBytes: Long get() = asset.sizeBytes()
    val sizeLabel: String get() = humanSize(sizeBytes)
    val canDownload: Boolean get() = status is VoiceResourceStatus.Missing || status is VoiceResourceStatus.Failed
}

/** A device the page offers; [spec] is what the dictation request's device spec receives. */
data class DeviceChoice(val spec: String, val label: String)

sealed interface LiveTestState {
    data object Idle : LiveTestState
    data object Loading : LiveTestState
    data object Listening : LiveTestState
    data object Stopped : LiveTestState
    data class Error(val message: String) : LiveTestState
}

private const val UNIT = 1024.0
private val UNITS = listOf("B", "KB", "MB", "GB")

fun humanSize(bytes: Long): String {
    var value = bytes.toDouble()
    var unit = 0
    while (value >= UNIT && unit < UNITS.lastIndex) {
        value /= UNIT
        unit++
    }
    return if (unit == 0) "$bytes B" else String.format(Locale.ROOT, "%.1f %s", value, UNITS[unit])
}
