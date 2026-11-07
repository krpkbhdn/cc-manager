package com.github.krpkbhdn.ccmanager.voice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild;
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset;
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService;
import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel;
import com.github.krpkbhdn.ccmanager.voice.audio.Microphone;
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import com.github.krpkbhdn.ccmanager.voice.device.DeviceService;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

class VoiceInputServiceTest {

    private static final RuntimeBuild CPU_BUILD = RuntimeBuild.WINDOWS_X64_CPU;
    private static final RuntimeBuild CUDA_BUILD = RuntimeBuild.WINDOWS_X64_CUDA12;
    private static final DictationRequest REQUEST = new DictationRequest(WhisperModel.BASE_Q5_1, null, null, null);

    @TempDir
    Path dir;

    private final DeviceService devices = mock(DeviceService.class);
    private final VoiceAssetService assets = mock(VoiceAssetService.class);
    private final Microphone microphone = mock(Microphone.class);
    private final ScheduledExecutorService scheduler = mock(ScheduledExecutorService.class);
    private final RecognizerOpener opener = mock(RecognizerOpener.class);
    private final Recognizer recognizer = mock(Recognizer.class);
    private final DictationListener listener = mock(DictationListener.class);
    private final AutoCloseable micHandle = mock(AutoCloseable.class);
    private final Path modelFile = Path.of("model.bin");
    private final Path vadFile = Path.of("vad.bin");
    private VoiceInputService service;
    private VoiceProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        when(devices.select("best")).thenReturn(ComputeDevice.CPU);
        when(devices.buildFor(ComputeDevice.CPU)).thenReturn(CPU_BUILD);
        when(devices.effectiveDevice(ComputeDevice.CPU)).thenReturn(ComputeDevice.CPU);
        when(assets.pathOf(any())).thenReturn(Optional.of(dir));
        when(assets.pathOf(WhisperModel.BASE_Q5_1.asset())).thenReturn(Optional.of(modelFile));
        when(assets.pathOf(VoiceAsset.SILERO_VAD)).thenReturn(Optional.of(vadFile));
        install(CPU_BUILD);
        install(CUDA_BUILD);
        when(opener.loadedDirectory()).thenReturn(Optional.empty());
        when(opener.open(any(), any(), any(), any(), any())).thenReturn(recognizer);
        when(microphone.open(any(), any())).thenReturn(micHandle);
        properties = new VoiceProperties("auto", Duration.ofSeconds(10), Duration.ofMillis(1500),
                Duration.ofMillis(700), 0.01, 0.5, 4, Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(60));
        service = new VoiceInputService(devices, assets, microphone, properties, opener, scheduler);
    }

    private void install(RuntimeBuild build) throws IOException {
        var installed = dir.resolve(build.name());
        var libraries = build.libraryDirectory(installed);
        Files.createDirectories(libraries);
        for (var name : build.libraries()) {
            Files.createFile(libraries.resolve(name));
        }
        if (build.flavor() == RuntimeBuild.Flavor.CUDA) {
            Files.createFile(libraries.resolve("ggml-cuda.dll"));
        }
        when(assets.pathOf(build.asset())).thenReturn(Optional.of(installed));
    }

    private Path libraries(RuntimeBuild build) {
        return build.libraryDirectory(dir.resolve(build.name())).toAbsolutePath().normalize();
    }

    private void opened(int times) {
        verify(opener, times(times)).open(any(), any(), any(), any(), any());
    }

    @Test
    void startNamesTheMissingAssetAndInstall() {
        when(assets.pathOf(WhisperModel.BASE_Q5_1.asset())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.start(REQUEST, listener))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(WhisperModel.BASE_Q5_1.asset().name())
                .hasMessageContaining("install");
        opened(0);
    }

    @Test
    void installFetchesBuildModelAndVad() throws IOException {
        service.install(REQUEST, progress -> { });

        verify(assets).install(eq(CPU_BUILD.asset()), any());
        verify(assets).install(eq(WhisperModel.BASE_Q5_1.asset()), any());
        verify(assets).install(eq(VoiceAsset.SILERO_VAD), any());
    }

    @Test
    void startOpensTheModelFromTheInstalledFiles() {
        service.start(REQUEST, listener).close();

        verify(opener).open(CPU_BUILD, libraries(CPU_BUILD), modelFile, vadFile, ComputeDevice.CPU);
    }

    @Test
    void secondSessionReusesTheWarmRecognizer() {
        var first = service.start(REQUEST, listener);
        var second = service.start(REQUEST, listener);

        opened(1);
        first.close();
        second.close();
    }

    @Test
    void anotherLanguageSharesTheRecognizer() {
        service.warmUp(REQUEST);
        service.warmUp(new DictationRequest(WhisperModel.BASE_Q5_1, null, null, "uk"));

        opened(1);
    }

    @Test
    void recognizerClosesOnlyAfterTheLastSessionAndIdleDelayAndReopensOnDemand() {
        var idleStop = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(idleStop.capture(), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenReturn(mock(ScheduledFuture.class));
        var first = service.start(REQUEST, listener);
        var second = service.start(REQUEST, listener);

        first.close();
        verify(scheduler, never()).schedule(any(Runnable.class), anyLong(), any());
        second.close();
        verify(recognizer, never()).close();
        idleStop.getValue().run();

        verify(recognizer).close();
        service.warmUp(REQUEST);
        opened(2);
    }

    @Test
    void anIdleStopOfAnOldSessionDoesNotCloseARecognizerAliveForANewSession() {
        var idleStop = ArgumentCaptor.forClass(Runnable.class);
        when(scheduler.schedule(idleStop.capture(), anyLong(), any())).thenReturn(mock(ScheduledFuture.class));
        service.start(REQUEST, listener).close();
        var current = service.start(REQUEST, listener);

        idleStop.getValue().run();

        verify(recognizer, never()).close();
        current.close();
    }

    @Test
    void newSessionCancelsThePendingIdleStop() {
        var pending = mock(ScheduledFuture.class);
        when(scheduler.schedule(any(Runnable.class), anyLong(), any())).thenReturn(pending);
        service.warmUp(REQUEST);

        var session = service.start(REQUEST, listener);

        verify(pending).cancel(false);
        session.close();
    }

    @Test
    void closingTheSessionStopsTheMicrophoneOnce() throws Exception {
        var session = service.start(REQUEST, listener);

        session.close();
        session.close();

        verify(micHandle).close();
    }

    @Test
    void failingMicrophoneReleasesTheRecognizer() {
        when(microphone.open(any(), any())).thenThrow(new IllegalStateException("no microphone"));
        when(scheduler.schedule(any(Runnable.class), anyLong(), any())).thenReturn(mock(ScheduledFuture.class));

        assertThatThrownBy(() -> service.start(REQUEST, listener)).hasMessageContaining("no microphone");

        verify(scheduler).schedule(any(Runnable.class), anyLong(), any());
    }

    @Test
    void shutdownClosesEveryWarmRecognizer() {
        service.warmUp(REQUEST);

        service.shutdown();

        verify(recognizer).close();
        verify(scheduler).shutdownNow();
    }

    @Test
    void startAfterShutdownFailsAndClosingALiveSessionDoesNotThrow() {
        var session = service.start(REQUEST, listener);
        service.shutdown();

        session.close();

        assertThatThrownBy(() -> service.start(REQUEST, listener)).isInstanceOf(IllegalStateException.class);
        opened(1);
    }

    @Test
    void shutdownIsNotBlockedByAColdOpenAndClosesTheLateRecognizer() throws Exception {
        var opening = new CountDownLatch(1);
        var proceed = new CountDownLatch(1);
        when(opener.open(any(), any(), any(), any(), any())).thenAnswer(call -> {
            opening.countDown();
            proceed.await();
            return recognizer;
        });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var cold = executor.submit(() -> service.warmUp(REQUEST));
            assertThat(opening.await(5, TimeUnit.SECONDS)).isTrue();

            assertTimeoutPreemptively(Duration.ofSeconds(5), service::shutdown);
            proceed.countDown();

            assertThatThrownBy(() -> cold.get(5, TimeUnit.SECONDS)).hasCauseInstanceOf(IllegalStateException.class);
            verify(recognizer).close();
        } finally {
            proceed.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void failedOpenIsReportedAndRetriedOnTheNextStart() {
        when(opener.open(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("boom"))
                .thenReturn(recognizer);

        assertThatThrownBy(() -> service.warmUp(REQUEST)).hasMessageContaining("boom");
        service.warmUp(REQUEST);

        opened(2);
    }

    @Test
    void errorFromOpenerIsReportedAndRetriedInsteadOfHanging() {
        when(opener.open(any(), any(), any(), any(), any())).thenThrow(new UnsatisfiedLinkError("no lib"))
                .thenReturn(recognizer);

        assertThatThrownBy(() -> service.warmUp(REQUEST)).isInstanceOf(UnsatisfiedLinkError.class);
        service.warmUp(REQUEST);

        opened(2);
    }

    @Test
    void requestRejectsMissingModelAndBadLanguage() {
        assertThatThrownBy(() -> new DictationRequest(null, null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DictationRequest(WhisperModel.BASE_Q5_1, null, null, "English"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(new DictationRequest(WhisperModel.BASE_Q5_1, null, null, "uk").language()).isEqualTo("uk");
        assertThat(new DictationRequest(WhisperModel.BASE_Q5_1, null, null, "auto").language()).isEqualTo("auto");
    }

    @Test
    void gpuWithoutAGpuBuildFallsBackToTheCpu() {
        var gpu = ComputeDevice.nvidia(0, "RTX");
        when(devices.select("best")).thenReturn(gpu);
        when(devices.buildFor(gpu)).thenReturn(CPU_BUILD);
        when(devices.effectiveDevice(gpu)).thenReturn(ComputeDevice.CPU);

        service.warmUp(REQUEST);

        verify(opener).open(CPU_BUILD, libraries(CPU_BUILD), modelFile, vadFile, ComputeDevice.CPU);
    }

    @Test
    void cpuRequestUsesTheAlreadyLoadedCudaRuntime() {
        when(opener.loadedDirectory()).thenReturn(Optional.of(libraries(CUDA_BUILD)));

        service.warmUp(REQUEST);

        verify(opener).open(CUDA_BUILD, libraries(CUDA_BUILD), modelFile, vadFile, ComputeDevice.CPU);
    }

    @Test
    void gpuRequestAfterACpuRuntimeWasLoadedNeedsARestart() {
        var gpu = ComputeDevice.nvidia(0, "RTX");
        when(devices.select("best")).thenReturn(gpu);
        when(devices.buildFor(gpu)).thenReturn(CUDA_BUILD);
        when(devices.effectiveDevice(gpu)).thenReturn(gpu);
        when(opener.loadedDirectory()).thenReturn(Optional.of(libraries(CPU_BUILD)));

        assertThatThrownBy(() -> service.warmUp(REQUEST)).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("restart the application");
        opened(0);
    }

    @Test
    void restartIsNotRequiredWhileNothingIsLoadedOrTheSameRuntimeIs() {
        assertThat(service.restartRequired(REQUEST)).isFalse();

        when(opener.loadedDirectory()).thenReturn(Optional.of(libraries(CPU_BUILD)));

        assertThat(service.restartRequired(REQUEST)).isFalse();
    }

    @Test
    void restartIsNotRequiredForACpuRequestOnALoadedCudaRuntime() {
        when(opener.loadedDirectory()).thenReturn(Optional.of(libraries(CUDA_BUILD)));

        assertThat(service.restartRequired(REQUEST)).isFalse();
    }

    @Test
    void restartIsRequiredForAGpuRequestOnALoadedCpuRuntime() {
        var gpu = ComputeDevice.nvidia(0, "RTX");
        when(devices.select("best")).thenReturn(gpu);
        when(devices.buildFor(gpu)).thenReturn(CUDA_BUILD);
        when(devices.effectiveDevice(gpu)).thenReturn(gpu);
        when(opener.loadedDirectory()).thenReturn(Optional.of(libraries(CPU_BUILD)));

        assertThat(service.restartRequired(REQUEST)).isTrue();
    }

    @Test
    void restartIsRequiredWhenTheLoadedDirectoryMatchesNoBuild() {
        when(opener.loadedDirectory()).thenReturn(Optional.of(dir.resolve("elsewhere")));

        assertThat(service.restartRequired(REQUEST)).isTrue();
        assertThatThrownBy(() -> service.warmUp(REQUEST)).hasMessageContaining("restart the application");
    }

    @Test
    void restartIsNotRequiredWhenTheRuntimeIsNotInstalledYet() {
        when(assets.pathOf(CPU_BUILD.asset())).thenReturn(Optional.empty());
        when(opener.loadedDirectory()).thenReturn(Optional.of(libraries(CUDA_BUILD)));

        assertThat(service.restartRequired(REQUEST)).isFalse();
    }

    @Test
    void incompleteRuntimeAsksToInstallAgain() throws IOException {
        Files.delete(libraries(CPU_BUILD).resolve("whisper.dll"));

        assertThatThrownBy(() -> service.warmUp(REQUEST)).hasMessageContaining("incomplete");
        opened(0);
    }

    @Test
    void sessionsOfDifferentDevicesOpenSeparately() {
        var gpu = ComputeDevice.nvidia(0, "RTX");
        when(devices.select("gpu")).thenReturn(gpu);
        when(devices.buildFor(gpu)).thenReturn(CUDA_BUILD);
        when(devices.effectiveDevice(gpu)).thenReturn(gpu);
        var opened = new ArrayList<ComputeDevice>();
        when(opener.open(any(), any(), any(), any(), any())).thenAnswer(call -> {
            opened.add(call.getArgument(4));
            return recognizer;
        });

        service.warmUp(REQUEST);
        service.warmUp(new DictationRequest(WhisperModel.BASE_Q5_1, "gpu", null, null));

        assertThat(opened).containsExactly(ComputeDevice.CPU, gpu);
    }
}
