package com.github.krpkbhdn.ccmanager.ui.voice

import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.state.UiSettings
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
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener
import java.io.IOException
import java.nio.file.Path
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.function.DoubleConsumer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class VoiceSettingsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val gpu = ComputeDevice.nvidia(0, "NVIDIA GeForce RTX 5080")
    private val installed = ConcurrentHashMap.newKeySet<VoiceAsset>()
    private val errors = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var settings = UiSettings()

    private val devices = mock(DeviceService::class.java).also {
        `when`(it.devices()).thenReturn(listOf(ComputeDevice.CPU, gpu))
        `when`(it.refresh()).thenReturn(listOf(ComputeDevice.CPU, gpu))
        `when`(it.select("best")).thenReturn(gpu)
        `when`(it.select("cpu")).thenReturn(ComputeDevice.CPU)
        `when`(it.select("NVIDIA GeForce RTX 5080")).thenReturn(gpu)
        `when`(it.select("Gone GPU")).thenThrow(IllegalArgumentException("Device not found: Gone GPU"))
        `when`(it.buildFor(ComputeDevice.CPU)).thenReturn(RuntimeBuild.WINDOWS_X64_CPU)
        `when`(it.buildFor(gpu)).thenReturn(RuntimeBuild.WINDOWS_X64_CUDA12)
    }
    private val assets = mock(VoiceAssetService::class.java).also {
        `when`(it.installed(any())).thenAnswer { call -> call.getArgument<VoiceAsset>(0) in installed }
        `when`(it.install(any(), any())).thenAnswer { call ->
            call.getArgument<DoubleConsumer?>(1)?.accept(0.5)
            call.getArgument<VoiceAsset?>(0)?.let { asset -> installed += asset }
            Path.of("installed")
        }
    }
    private val microphone = mock(Microphone::class.java).also { `when`(it.inputs()).thenReturn(listOf("USB mic")) }
    private val voice = mock(VoiceInputService::class.java)
    private val state by lazy {
        VoiceSettingsState(VoiceBackend(voice, devices, assets, microphone), scope, { settings }, { settings = it(settings) }, { _, message -> errors += message })
    }

    @AfterEach
    fun stop() = scope.cancel()

    private fun ready(): VoiceSettingsState {
        installed += listOf(VoiceAsset.SILERO_VAD, WhisperModel.BASE_Q5_1.asset(), RuntimeBuild.WINDOWS_X64_CUDA12.asset())
        await { state.resources.size == 3 && state.deviceChoices.size == 3 && state.inputs.isNotEmpty() }
        return state
    }

    private fun capture(session: DictationSession = mock(DictationSession::class.java)): () -> DictationListener? {
        var captured: DictationListener? = null
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer {
            captured = it.getArgument(1)
            session
        }
        return { captured }
    }

    @Test
    fun `toggling enabled is saved in the settings`() {
        state.setEnabled(true)

        assertThat(settings.voiceEnabled).isTrue()
        assertThat(state.enabled).isTrue()
    }

    @Test
    fun `choices are saved in the settings`() {
        state.selectModel(WhisperModel.SMALL_Q5_1)
        state.selectDevice("cpu")
        state.selectInput("USB mic")

        assertThat(settings.voiceModel).isEqualTo("small-q5_1")
        assertThat(settings.voiceDevice).isEqualTo("cpu")
        assertThat(settings.voiceInput).isEqualTo("USB mic")
        assertThat(state.model).isEqualTo(WhisperModel.SMALL_Q5_1)
    }

    @Test
    fun `devices offer auto, the CPU and every GPU and microphones are listed`() {
        await { state.deviceChoices.size == 3 && state.inputs.isNotEmpty() }

        assertThat(state.deviceChoices.map { it.label }).containsExactly("Best available (auto)", "CPU", "NVIDIA GeForce RTX 5080")
        assertThat(state.deviceChoices.map { it.spec }).containsExactly("best", "cpu", "NVIDIA GeForce RTX 5080")
        assertThat(state.inputs).containsExactly("USB mic")
    }

    @Test
    fun `resources are the runtime of the effective device, the chosen model and the voice activity model`() {
        installed += VoiceAsset.SILERO_VAD

        await { state.resources.size == 3 }

        assertThat(state.resources.map { it.asset }).containsExactly(RuntimeBuild.WINDOWS_X64_CUDA12.asset(), WhisperModel.BASE_Q5_1.asset(), VoiceAsset.SILERO_VAD)
        assertThat(state.resources.map { it.status }).containsExactly(VoiceResourceStatus.Missing, VoiceResourceStatus.Missing, VoiceResourceStatus.Installed)
        assertThat(state.resources.first().sizeLabel).isEqualTo("639.5 MB")
    }

    @Test
    fun `changing device or model recomputes the resources`() {
        await { state.resources.size == 3 }

        state.selectDevice("cpu")
        await { state.resources.first().asset == RuntimeBuild.WINDOWS_X64_CPU.asset() }
        state.selectModel(WhisperModel.LARGE_V3_TURBO_Q5_0)
        await { state.resources[1].asset == WhisperModel.LARGE_V3_TURBO_Q5_0.asset() }
    }

    @Test
    fun `a download ends installed`() {
        await { state.resources.size == 3 }

        state.download(state.resources[1])

        await { state.resources[1].status == VoiceResourceStatus.Installed }
        assertThat(state.resources[0].status).isEqualTo(VoiceResourceStatus.Missing)
    }

    @Test
    fun `a download shows its progress`() {
        val release = CountDownLatch(1)
        `when`(assets.install(any(), any())).thenAnswer { call ->
            call.getArgument<DoubleConsumer>(1).accept(0.25)
            release.await()
            Path.of("installed")
        }
        await { state.resources.size == 3 }

        state.download(state.resources[1])

        await { state.resources[1].status == VoiceResourceStatus.Downloading(0.25f) }
        release.countDown()
    }

    @Test
    fun `a failed download shows in its row only and can be retried`() {
        `when`(assets.install(any(), any())).thenThrow(IOException("checksum mismatch")).thenAnswer { installed += it.getArgument<VoiceAsset>(0); Path.of("x") }
        await { state.resources.size == 3 }

        state.download(state.resources[1])
        await { state.resources[1].status == VoiceResourceStatus.Failed("checksum mismatch") }
        assertThat(errors).isEmpty()
        assertThat(state.resources[1].canDownload).isTrue()

        state.download(state.resources[1])
        await { state.resources[1].status == VoiceResourceStatus.Installed }
        assertThat(state.resources.none { it.status is VoiceResourceStatus.Failed }).isTrue()
    }

    @Test
    fun `download all fetches the missing ones one after another`() {
        val running = AtomicInteger()
        val overlap = AtomicBoolean()
        val order = mutableListOf<VoiceAsset>()
        `when`(assets.install(any(), any())).thenAnswer { call ->
            if (running.incrementAndGet() > 1) overlap.set(true)
            Thread.sleep(50)
            synchronized(order) { order += call.getArgument<VoiceAsset>(0) }
            installed += call.getArgument<VoiceAsset>(0)
            running.decrementAndGet()
            Path.of("x")
        }
        installed += VoiceAsset.SILERO_VAD
        await { state.resources.size == 3 }

        state.downloadAllMissing()

        await { state.resources.all { it.status == VoiceResourceStatus.Installed } }
        assertThat(overlap.get()).isFalse()
        assertThat(order).containsExactly(RuntimeBuild.WINDOWS_X64_CUDA12.asset(), WhisperModel.BASE_Q5_1.asset())
    }

    @Test
    fun `live test is unavailable while something is missing or voice is off`() {
        installed += listOf(VoiceAsset.SILERO_VAD, WhisperModel.BASE_Q5_1.asset())
        await { state.resources.size == 3 }
        state.setEnabled(true)
        assertThat(state.canLiveTest).isFalse()

        state.startLiveTest()

        assertThat(state.liveTest).isEqualTo(LiveTestState.Idle)
        installed += RuntimeBuild.WINDOWS_X64_CUDA12.asset()
        state.selectModel(WhisperModel.BASE_Q5_1)
        await { state.canLiveTest }
        state.setEnabled(false)
        assertThat(state.canLiveTest).isFalse()
    }

    @Test
    fun `live test goes loading, listening and stopped and closes the session`() {
        val session = mock(DictationSession::class.java)
        val loading = CountDownLatch(1)
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer { loading.await(); session }
        val ready = ready()
        ready.setEnabled(true)

        ready.startLiveTest()
        assertThat(ready.liveTest).isEqualTo(LiveTestState.Loading)
        loading.countDown()
        await { ready.liveTest == LiveTestState.Listening }
        ready.stopLiveTest()

        await { ready.liveTest == LiveTestState.Stopped }
        verify(session).close()
    }

    @Test
    fun `live test asks for the chosen model, device and microphone`() {
        capture()
        val ready = ready()
        ready.setEnabled(true)
        ready.selectInput("USB mic")

        ready.startLiveTest()

        await { ready.liveTest == LiveTestState.Listening }
        verify(voice).start(eq(DictationRequest(WhisperModel.BASE_Q5_1, "best", "USB mic", null)), any<DictationListener>())
    }

    @Test
    fun `partial and final results build the live text`() {
        val listener = capture()
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        listener()!!.onPartial("hello wor")
        assertThat(ready.liveText).isEqualTo("hello wor")
        listener()!!.onFinal("hello world.")
        listener()!!.onPartial("and  more")

        assertThat(ready.liveText).isEqualTo("hello world. and more")
        listener()!!.onFinal("x".repeat(200))
        assertThat(ready.liveText).isEqualTo("..." + "x".repeat(128))
    }

    @Test
    fun `a failing start shows the backend message`() {
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenThrow(IllegalStateException("restart the application to use whisper.cpp CUDA"))
        val ready = ready()
        ready.setEnabled(true)

        ready.startLiveTest()

        await { ready.liveTest is LiveTestState.Error }
        assertThat(ready.liveTest).isEqualTo(LiveTestState.Error("restart the application to use whisper.cpp CUDA"))
    }

    @Test
    fun `a new live test clears the previous text`() {
        val listener = capture()
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }
        listener()!!.onFinal("old text")
        ready.stopLiveTest()
        await { ready.liveTest == LiveTestState.Stopped }

        ready.startLiveTest()

        assertThat(ready.liveText).isEmpty()
    }

    @Test
    fun `close stops a running live test`() {
        val session = mock(DictationSession::class.java)
        capture(session)
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        ready.close()

        await { ready.liveTest == LiveTestState.Stopped }
        verify(session).close()
    }

    @Test
    fun `turning voice off stops a running live test`() {
        val session = mock(DictationSession::class.java)
        capture(session)
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        ready.setEnabled(false)

        await { ready.liveTest == LiveTestState.Stopped }
        verify(session).close()
    }

    @Test
    fun `an error reported while listening releases the session`() {
        val session = mock(DictationSession::class.java)
        val listener = capture(session)
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        listener()!!.onError(IllegalStateException("device lost"))

        assertThat(ready.liveTest).isEqualTo(LiveTestState.Error("device lost"))
        verify(session, org.mockito.Mockito.timeout(2000)).close()
    }

    @Test
    fun `stop while loading is not turned into an error by a later start failure`() {
        val release = CountDownLatch(1)
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer { release.await(); throw IllegalStateException("boom") }
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()

        ready.stopLiveTest()
        release.countDown()
        Thread.sleep(300)

        assertThat(ready.liveTest).isEqualTo(LiveTestState.Stopped)
    }

    @Test
    fun `quick start stop start ends with one live session and closes the abandoned ones`() {
        val sessions = java.util.concurrent.CopyOnWriteArrayList<DictationSession>()
        val starts = AtomicInteger()
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer {
            starts.incrementAndGet()
            Thread.sleep(100)
            mock(DictationSession::class.java).also { sessions += it }
        }
        val ready = ready()
        ready.setEnabled(true)

        ready.startLiveTest()
        await { starts.get() == 1 }
        ready.stopLiveTest()
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }
        Thread.sleep(300)

        assertThat(sessions).hasSize(2)
        verify(sessions[0]).close()
        org.mockito.Mockito.verify(sessions[1], org.mockito.Mockito.never()).close()
        assertThat(ready.liveTest).isEqualTo(LiveTestState.Listening)
    }

    @Test
    fun `a failed download does not stay downloading and download all stops at it`() {
        `when`(assets.install(any(), any())).thenThrow(IllegalStateException("disk full"))
        await { state.resources.size == 3 }

        state.downloadAllMissing()

        await { state.resources.any { it.status is VoiceResourceStatus.Failed } }
        await { !state.downloading }
        assertThat(state.resources.none { it.status is VoiceResourceStatus.Downloading }).isTrue()
        assertThat(state.canDownloadAll).isTrue()
        assertThat(errors).containsExactly("disk full")
        verify(assets, org.mockito.Mockito.times(1)).install(any(), any())
        assertThat(state.resources.count { it.status == VoiceResourceStatus.Missing }).isEqualTo(2)
    }

    @Test
    fun `an error while listening lets the test start again`() {
        val listener = capture()
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }
        listener()!!.onError(IllegalStateException("device lost"))
        assertThat(ready.canLiveTest).isTrue()

        ready.startLiveTest()

        await { ready.liveTest == LiveTestState.Listening }
        verify(voice, org.mockito.Mockito.times(2)).start(any<DictationRequest>(), any<DictationListener>())
    }

    @Test
    fun `stop while loading closes the session that arrives late`() {
        val session = mock(DictationSession::class.java)
        val release = CountDownLatch(1)
        val started = AtomicBoolean()
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer { started.set(true); release.await(); session }
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { started.get() }
        ready.stopLiveTest()
        assertThat(ready.liveTest).isEqualTo(LiveTestState.Stopped)

        release.countDown()

        verify(session, org.mockito.Mockito.timeout(2000)).close()
        assertThat(ready.liveTest).isEqualTo(LiveTestState.Stopped)
    }

    @Test
    fun `a second start waits until the first one has finished`() {
        val release = CountDownLatch(1)
        val running = AtomicInteger()
        val overlap = AtomicBoolean()
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer {
            if (running.incrementAndGet() > 1) overlap.set(true)
            release.await()
            running.decrementAndGet()
            mock(DictationSession::class.java)
        }
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { running.get() == 1 }

        ready.stopLiveTest()
        ready.startLiveTest()
        Thread.sleep(200)
        verify(voice, org.mockito.Mockito.times(1)).start(any<DictationRequest>(), any<DictationListener>())
        release.countDown()

        await { ready.liveTest == LiveTestState.Listening }
        assertThat(overlap.get()).isFalse()
    }

    @Test
    fun `close releases the microphone before it returns`() {
        val session = mock(DictationSession::class.java)
        capture(session)
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        ready.close()

        verify(session).close()
        assertThat(ready.liveTest).isEqualTo(LiveTestState.Stopped)
    }

    @Test
    fun `a session returned after the scope was cancelled is still closed`() {
        val session = mock(DictationSession::class.java)
        val release = CountDownLatch(1)
        val started = AtomicBoolean()
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer { started.set(true); release.await(); session }
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { started.get() }

        ready.close()
        scope.cancel()
        release.countDown()

        verify(session, org.mockito.Mockito.timeout(2000)).close()
    }

    @Test
    fun `turning voice off while loading stops the test`() {
        val release = CountDownLatch(1)
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer { release.await(); mock(DictationSession::class.java) }
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()

        ready.setEnabled(false)
        release.countDown()

        assertThat(ready.liveTest).isEqualTo(LiveTestState.Stopped)
        assertThat(ready.canLiveTest).isFalse()
    }

    @Test
    fun `the live text keeps only a bounded window of what was heard`() {
        val listener = capture()
        val ready = ready()
        ready.setEnabled(true)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        repeat(2000) { listener()!!.onFinal("word$it") }
        listener()!!.onPartial("tail")

        assertThat(ready.liveText).startsWith("...").endsWith("word1999 tail").hasSize(131)
    }

    @Test
    fun `an unknown saved model alias falls back to base`() {
        settings = settings.copy(voiceModel = "gigantic")

        await { state.resources.size == 3 }

        assertThat(state.model).isEqualTo(WhisperModel.BASE_Q5_1)
        assertThat(state.resources.map { it.asset }).contains(WhisperModel.BASE_Q5_1.asset())
    }

    @Test
    fun `a vanished GPU means Best everywhere and says so`() {
        settings = settings.copy(voiceDevice = "Gone GPU", voiceEnabled = true)
        capture()
        val ready = ready()

        assertThat(ready.deviceLabel).isEqualTo("Gone GPU (not found, using Best)")
        assertThat(ready.resources.map { it.asset }).contains(RuntimeBuild.WINDOWS_X64_CUDA12.asset())
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }
        verify(voice).start(eq(DictationRequest(WhisperModel.BASE_Q5_1, "best", "", null)), any<DictationListener>())
    }

    @Test
    fun `a blank saved device is Best without a warning`() {
        settings = settings.copy(voiceDevice = "  ", voiceEnabled = true)
        capture()
        val ready = ready()

        assertThat(ready.deviceLabel).isEqualTo("Best available (auto)")
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }
        verify(voice).start(eq(DictationRequest(WhisperModel.BASE_Q5_1, "best", "", null)), any<DictationListener>())
    }

    @Test
    fun `the saved GPU is labelled by its name`() {
        settings = settings.copy(voiceDevice = "NVIDIA GeForce RTX 5080")

        await { state.resources.size == 3 && state.deviceChoices.size == 3 }

        assertThat(state.deviceLabel).isEqualTo("NVIDIA GeForce RTX 5080")
    }

    @Test
    fun `a saved microphone that is gone is not shown as selected`() {
        settings = settings.copy(voiceInput = "Unplugged mic", voiceEnabled = true)
        capture()
        val ready = ready()

        assertThat(ready.input).isEmpty()
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }
        verify(voice).start(eq(DictationRequest(WhisperModel.BASE_Q5_1, "best", "", null)), any<DictationListener>())
    }

    @Test
    fun `refreshing devices lists the new ones and microphones again`() {
        await { state.deviceChoices.size == 3 && state.inputs.isNotEmpty() }
        val second = ComputeDevice.nvidia(1, "NVIDIA GeForce RTX 4090")
        `when`(devices.refresh()).thenReturn(listOf(ComputeDevice.CPU, gpu, second))
        `when`(microphone.inputs()).thenReturn(listOf("USB mic", "Headset"))

        state.refreshDevices()

        await { state.deviceChoices.size == 4 && state.inputs.size == 2 }
        verify(devices).refresh()
    }

    @Test
    fun `failing device and microphone lookups are reported and the resources are still computed`() {
        `when`(devices.devices()).thenThrow(IllegalStateException("probe crashed"))
        `when`(microphone.inputs()).thenThrow(IllegalStateException("no audio"))

        await { state.resources.size == 3 }

        assertThat(state.deviceChoices.map { it.spec }).containsExactly("best", "cpu")
        assertThat(state.inputs).isEmpty()
        assertThat(errors).hasSize(2).anyMatch { it.contains("probe crashed") }.anyMatch { it.contains("no audio") }
    }

    @Test
    fun `the last of several quick choices wins`() {
        await { state.resources.size == 3 }

        state.selectModel(WhisperModel.LARGE_V3_TURBO_Q5_0)
        state.selectDevice("cpu")
        state.selectModel(WhisperModel.SMALL_Q5_1)
        state.selectDevice("best")
        state.selectModel(WhisperModel.LARGE_V3_TURBO)

        await { state.resources.map { it.asset } == listOf(RuntimeBuild.WINDOWS_X64_CUDA12.asset(), WhisperModel.LARGE_V3_TURBO.asset(), VoiceAsset.SILERO_VAD) }
        Thread.sleep(200)
        assertThat(state.resources[1].asset).isEqualTo(WhisperModel.LARGE_V3_TURBO.asset())
    }

    @Test
    fun `download all is not offered while a download runs`() {
        val release = CountDownLatch(1)
        `when`(assets.install(any(), any())).thenAnswer { release.await(); Path.of("x") }
        await { state.resources.size == 3 }

        state.downloadAllMissing()

        await { state.downloading }
        assertThat(state.canDownloadAll).isFalse()
        release.countDown()
        await { !state.downloading }
    }

    @Test
    fun `changing the model during a download keeps the list right and the download going`() {
        val release = CountDownLatch(1)
        `when`(assets.install(any(), any())).thenAnswer { call ->
            release.await()
            installed += call.getArgument<VoiceAsset>(0)
            Path.of("x")
        }
        installed += VoiceAsset.SILERO_VAD
        await { state.resources.size == 3 }
        state.download(state.resources[1])
        await { state.resources[1].status is VoiceResourceStatus.Downloading }

        state.selectModel(WhisperModel.SMALL_Q5_1)
        await { state.resources[1].asset == WhisperModel.SMALL_Q5_1.asset() }

        assertThat(state.resources[1].status).isEqualTo(VoiceResourceStatus.Missing)
        release.countDown()
        await { !state.downloading }
        state.selectModel(WhisperModel.BASE_Q5_1)
        await { state.resources[1].asset == WhisperModel.BASE_Q5_1.asset() }
        assertThat(state.resources[1].status).isEqualTo(VoiceResourceStatus.Installed)
    }

    @Test
    fun `a request for the other runtime needs a restart, blocks the live test and goes away with the choice`() {
        `when`(voice.restartRequired(any<DictationRequest>())).thenAnswer { it.getArgument<DictationRequest>(0).deviceSpec() == "best" }
        installed += listOf(VoiceAsset.SILERO_VAD, WhisperModel.BASE_Q5_1.asset(), RuntimeBuild.WINDOWS_X64_CUDA12.asset(), RuntimeBuild.WINDOWS_X64_CPU.asset())
        state.setEnabled(true)

        await { state.restartRequired }
        assertThat(state.canLiveTest).isFalse()
        assertThat(state.liveTestBlocker).isEqualTo("Restart the app to switch between CPU and GPU")
        state.selectDevice("cpu")

        await { !state.restartRequired }
        await { state.canLiveTest }
    }

    @Test
    fun `no restart is asked for while the service sees none`() {
        val ready = ready()

        assertThat(ready.restartRequired).isFalse()
    }

    @Test
    fun `the live test blocker says why`() {
        val ready = ready()
        assertThat(ready.liveTestBlocker).isEqualTo("Enable voice input first")

        ready.setEnabled(true)
        assertThat(ready.liveTestBlocker).isNull()
        installed -= VoiceAsset.SILERO_VAD
        ready.selectModel(WhisperModel.BASE_Q5_1)

        await { ready.liveTestBlocker == "Download all components first" }
    }

    @Test
    fun `thirty rapid start stop cycles leave no open session and the test can start again`() {
        val sessions = java.util.concurrent.CopyOnWriteArrayList<DictationSession>()
        `when`(voice.start(any<DictationRequest>(), any<DictationListener>())).thenAnswer {
            Thread.sleep(5)
            mock(DictationSession::class.java).also { sessions += it }
        }
        val ready = ready()
        ready.setEnabled(true)

        repeat(30) { ready.startLiveTest(); ready.stopLiveTest() }
        await { !ready.liveTestActive }
        Thread.sleep(300)
        ready.startLiveTest()
        await { ready.liveTest == LiveTestState.Listening }

        val open = sessions.filter { session -> runCatching { verify(session).close() }.isFailure }
        assertThat(open).hasSize(1)
    }

    @Test
    fun `closing the page during a running download does not hang and a later download can run`() {
        val release = CountDownLatch(1)
        `when`(assets.install(any(), any())).thenAnswer { release.await(5, java.util.concurrent.TimeUnit.SECONDS); Path.of("x") }
        await { state.resources.size == 3 }
        state.downloadAllMissing()
        await { state.downloading }

        state.close()
        release.countDown()

        await { !state.downloading }
    }

    @Test
    fun `two GPUs with the same name are chosen by id and resolve to the right one`() {
        val second = ComputeDevice.nvidia(1, gpu.label())
        `when`(devices.devices()).thenReturn(listOf(ComputeDevice.CPU, gpu, second))
        `when`(devices.select("gpu1")).thenReturn(second)
        `when`(devices.buildFor(second)).thenReturn(RuntimeBuild.WINDOWS_X64_CUDA12)
        settings = settings.copy(voiceDevice = "gpu1")

        await { state.deviceChoices.size == 4 }

        assertThat(state.deviceChoices.map { it.label }).contains("NVIDIA GeForce RTX 5080 (GPU 1)")
        await { state.deviceLabel == "NVIDIA GeForce RTX 5080 (GPU 1)" }
    }
}
