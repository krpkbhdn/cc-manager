package com.github.krpkbhdn.ccmanager.ui.voice

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
import com.github.krpkbhdn.ccmanager.voice.DictationRequest
import com.github.krpkbhdn.ccmanager.voice.DictationSession
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset
import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

private const val HARDWARE_FAILED_TITLE = "Voice input"
private const val DEVICES_FAILED = "Could not list compute devices: "
private const val INPUTS_FAILED = "Could not list microphones: "
private const val RESTART_HINT = "Restart the app to switch between CPU and GPU"
private const val DOWNLOAD_FIRST_HINT = "Download all components first"
private const val ENABLE_FIRST_HINT = "Enable voice input first"

/**
 * Compose state of the voice input settings page: choices, the downloads they need, restart warning and a live dictation test. What the choices
 * imply (needed downloads, device, restart) is recomputed one run at a time, each run reading the latest settings.
 */
class VoiceSettingsState(
    private val backend: VoiceBackend,
    private val scope: CoroutineScope,
    private val settings: () -> UiSettings,
    private val update: ((UiSettings) -> UiSettings) -> Unit,
    private val reportError: (title: String, message: String) -> Unit,
) {
    val models: List<WhisperModel> = WhisperModel.entries

    var deviceChoices by mutableStateOf(deviceChoicesOf(emptyList()))
        private set
    var inputs by mutableStateOf<List<String>>(emptyList())
        private set
    private var needed by mutableStateOf<List<VoiceAsset>>(emptyList())
    private var installed by mutableStateOf<Set<VoiceAsset>>(emptySet())
    private var resolved by mutableStateOf(ResolvedDevice(BEST_DEVICE, null, notFound = false))
    private val recomputing = Mutex()
    private val downloads = ResourceDownloads(backend.assets, scope, reportError) { recompute() }
    private val live = LiveTestController(backend.input, scope)
    private val dictationStart = Any()
    @Volatile private var dictationSession: DictationSession? = null

    /** True when the chosen device needs a whisper runtime other than the one this process already loaded. */
    var restartRequired by mutableStateOf(false)
        private set

    val enabled: Boolean get() = settings().voiceEnabled
    val model: WhisperModel get() = modelOf(settings())
    val deviceLabel: String get() = deviceLabelOf(deviceChoices, resolved.spec, settings().voiceDevice, resolved.notFound)

    /** The saved microphone, or the system default (blank) when it is not plugged in any more. */
    val input: String get() = settings().voiceInput.takeIf { it.isBlank() || inputs.isEmpty() || it in inputs }.orEmpty()

    val resources: List<VoiceResource>
        get() = needed.map { VoiceResource(it, downloads.statusOf(it) ?: if (it in installed) VoiceResourceStatus.Installed else VoiceResourceStatus.Missing) }
    val downloading: Boolean get() = downloads.active
    val canDownloadAll: Boolean get() = !downloading && resources.any { it.canDownload }
    val liveTest: LiveTestState get() = live.state
    val liveText: String get() = live.text
    val liveTestActive: Boolean get() = live.active

    /** Why the live test cannot start now, null when it can. */
    val liveTestBlocker: String?
        get() = when {
            !enabled -> ENABLE_FIRST_HINT
            restartRequired -> RESTART_HINT
            resources.isEmpty() || resources.any { it.status != VoiceResourceStatus.Installed } -> DOWNLOAD_FIRST_HINT
            else -> null
        }
    val canLiveTest: Boolean get() = !liveTestActive && liveTestBlocker == null

    /** True when hold-Space dictation may start: voice is ready and the live test does not hold the microphone. */
    val dictationUsable: Boolean get() = liveTestBlocker == null && !liveTestActive

    init {
        launchRecompute { listInputs(); listDevices(refresh = false) }
    }

    fun setEnabled(value: Boolean) {
        update { it.copy(voiceEnabled = value) }
        if (!value) stopLiveTest()
    }

    fun selectModel(selected: WhisperModel) {
        update { it.copy(voiceModel = selected.alias()) }
        launchRecompute()
    }

    fun selectDevice(spec: String) {
        update { it.copy(voiceDevice = spec) }
        launchRecompute()
    }

    fun selectInput(name: String) = update { it.copy(voiceInput = name) }

    fun refreshDevices() = launchRecompute { listInputs(); listDevices(refresh = true) }

    fun download(resource: VoiceResource) = downloads.start(listOf(resource.asset))

    fun downloadAllMissing() = downloads.start(resources.filter { it.canDownload }.map { it.asset })

    fun startLiveTest() {
        if (canLiveTest) live.start(dictationRequest())
    }

    fun stopLiveTest() = live.stop()

    /** Blocks while the model loads: call off the UI thread. */
    fun startDictation(listener: DictationListener): DictationSession = synchronized(dictationStart) {
        backend.input.start(dictationRequest(), listener).also { dictationSession = it }
    }

    fun dictationFailed(message: String) = reportError(HARDWARE_FAILED_TITLE, message)

    /** Stops a running live test and waits for the microphone; call when the page, dialog or app goes away. */
    fun close() {
        live.close()
        dictationSession?.close()
    }

    private fun dictationRequest() = DictationRequest(model, resolved.spec, input, null)

    private fun modelOf(chosen: UiSettings) = WhisperModel.byAlias(chosen.voiceModel).orElse(WhisperModel.BASE_Q5_1)

    private fun launchRecompute(beforehand: () -> Unit = {}) {
        scope.launch { recompute(beforehand) }
    }

    private suspend fun recompute(beforehand: () -> Unit = {}): Unit = withContext(Dispatchers.IO) {
        recomputing.withLock {
            beforehand()
            val chosen = settings()
            val device = resolveDevice(chosen.voiceDevice)
            val build = device.device?.let(::runtimeBuild)
            val assets = listOfNotNull(build?.asset(), modelOf(chosen).asset(), VoiceAsset.SILERO_VAD)
            val present = assets.filter(backend.assets::installed).toSet()
            resolved = device
            needed = assets
            installed = present
            restartRequired = build != null && restartRequiredFor(DictationRequest(modelOf(chosen), device.spec, null, null))
            downloads.forgetFailedIn(present)
        }
    }

    private fun listDevices(refresh: Boolean) {
        deviceChoices = try {
            deviceChoicesOf(if (refresh) backend.devices.refresh() else backend.devices.devices())
        } catch (e: RuntimeException) {
            reportError(HARDWARE_FAILED_TITLE, DEVICES_FAILED + (e.message ?: UNKNOWN_ERROR))
            deviceChoicesOf(emptyList())
        }
    }

    private fun listInputs() {
        inputs = try {
            backend.microphone.inputs()
        } catch (e: RuntimeException) {
            reportError(HARDWARE_FAILED_TITLE, INPUTS_FAILED + (e.message ?: UNKNOWN_ERROR))
            emptyList()
        }
    }

    /** A saved device that is gone (unplugged GPU, hand-edited value) means Best, everywhere the choice is used. */
    private fun resolveDevice(saved: String): ResolvedDevice {
        val wanted = saved.ifBlank { BEST_DEVICE }
        return try {
            ResolvedDevice(wanted, backend.devices.select(wanted), notFound = false)
        } catch (e: IllegalArgumentException) {
            ResolvedDevice(BEST_DEVICE, selectBest(), notFound = true)
        }
    }

    private fun selectBest(): ComputeDevice? = try {
        backend.devices.select(BEST_DEVICE)
    } catch (e: RuntimeException) {
        null
    }

    private fun runtimeBuild(device: ComputeDevice): RuntimeBuild? = try {
        backend.devices.buildFor(device)
    } catch (e: IllegalStateException) {
        null
    }

    private fun restartRequiredFor(request: DictationRequest): Boolean = try {
        backend.input.restartRequired(request)
    } catch (e: RuntimeException) {
        false
    }

    private data class ResolvedDevice(val spec: String, val device: ComputeDevice?, val notFound: Boolean)
}
