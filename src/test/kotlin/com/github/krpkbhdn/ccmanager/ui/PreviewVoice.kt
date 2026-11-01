package com.github.krpkbhdn.ccmanager.ui

import com.github.krpkbhdn.ccmanager.ui.voice.VoiceBackend
import com.github.krpkbhdn.ccmanager.voice.DictationRequest
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.VoiceInputService
import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService
import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel
import com.github.krpkbhdn.ccmanager.voice.audio.Microphone
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice
import com.github.krpkbhdn.ccmanager.voice.device.DeviceService
import com.github.krpkbhdn.ccmanager.voice.device.GpuProbe
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import java.io.IOException
import java.nio.file.Path
import java.util.Optional
import java.util.concurrent.ConcurrentHashMap
import java.util.function.DoubleConsumer
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

private const val DOWNLOAD_STEPS = 10
private const val STEP_MILLIS = 150L
private val SAMPLE_PHRASES = listOf(
    "This is a",
    "This is a live test of voice input.",
    "This is a live test of voice input. It keeps listening while I talk, and only the end of a long sentence stays visible so the box never grows past three lines.",
)

/** Voice services for the dev preview: a CPU and an RTX 5080, the CPU runtime, the base model and the VAD installed, fake downloads and fake dictation; [failDownloads] and [restartRequired] stage the error states. */
class PreviewVoice {
    private val installed = ConcurrentHashMap.newKeySet<VoiceAsset>().apply {
        addAll(listOf(VoiceAsset.SILERO_VAD, WhisperModel.BASE_Q5_1.asset()) + RuntimeBuild.forCurrentPlatform().filter { it.flavor() == RuntimeBuild.Flavor.CPU }.map { it.asset() })
    }

    @Volatile var failDownloads = false
    @Volatile var restartRequired = false

    val devices = DeviceService(listOf(GpuProbe { listOf(ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080")) }))

    val assets: VoiceAssetService = mock(VoiceAssetService::class.java).also { service ->
        `when`(service.installed(any())).thenAnswer { it.getArgument<VoiceAsset>(0) in installed }
        `when`(service.pathOf(any())).thenAnswer { Optional.ofNullable(Path.of("preview").takeIf { _ -> it.getArgument<VoiceAsset>(0) in installed }) }
        `when`(service.install(any(), any())).thenAnswer {
            if (failDownloads) throw IOException("Checksum mismatch, the file was not saved")
            repeat(DOWNLOAD_STEPS) { step ->
                Thread.sleep(STEP_MILLIS)
                it.getArgument<DoubleConsumer>(1).accept((step + 1).toDouble() / DOWNLOAD_STEPS)
            }
            installed += it.getArgument<VoiceAsset>(0)
            Path.of("preview")
        }
    }

    val microphone: Microphone = mock(Microphone::class.java).also {
        `when`(it.inputs()).thenReturn(listOf("Microphone (USB Audio Device)", "Headset Microphone (Realtek Audio)"))
    }

    val input: VoiceInputService = mock(VoiceInputService::class.java).also { service ->
        `when`(service.restartRequired(any())).thenAnswer { restartRequired }
        `when`(service.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer {
            val listener = it.getArgument<DictationListener>(1)
            Thread {
                SAMPLE_PHRASES.forEach { phrase -> Thread.sleep(STEP_MILLIS * 2); listener.onPartial(phrase) }
                listener.onFinal(SAMPLE_PHRASES.last())
            }.apply { isDaemon = true }.start()
            mock(DictationSession::class.java)
        }
    }

    val backend get() = VoiceBackend(input, devices, assets, microphone)
}
