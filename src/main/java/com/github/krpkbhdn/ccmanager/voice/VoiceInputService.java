package com.github.krpkbhdn.ccmanager.voice;

import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild;
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAsset;
import com.github.krpkbhdn.ccmanager.voice.asset.VoiceAssetService;
import com.github.krpkbhdn.ccmanager.voice.audio.Microphone;
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import com.github.krpkbhdn.ccmanager.voice.device.DeviceService;
import com.github.krpkbhdn.ccmanager.voice.stream.Dictation;
import com.github.krpkbhdn.ccmanager.voice.stream.DictationListener;
import com.github.krpkbhdn.ccmanager.voice.stream.SpeechGate;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.DoubleConsumer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Starts dictation sessions on a warm in-process recognizer shared by every session with the same model and device. Nothing is downloaded here: missing assets fail the start until {@link #install} ran. A process loads one
 * whisper runtime (CPU or CUDA), so asking for the other one fails until the app restarts.
 */
@Component
public class VoiceInputService {

    private static final String DEFAULT_DEVICE = "best";

    private record Plan(WarmRecognizers.Key key, RuntimeBuild build, ComputeDevice device) {}

    private record Runtime(RuntimeBuild build, Path directory) {}

    private final DeviceService devices;
    private final VoiceAssetService assets;
    private final Microphone microphone;
    private final VoiceProperties properties;
    private final RecognizerOpener opener;
    private final WarmRecognizers recognizers;

    @Autowired
    public VoiceInputService(DeviceService devices, VoiceAssetService assets, Microphone microphone,
            VoiceProperties properties) {
        this(devices, assets, microphone, properties, RecognizerOpener.ffm(properties),
                Executors.newSingleThreadScheduledExecutor(VoiceInputService::daemon));
    }

    VoiceInputService(DeviceService devices, VoiceAssetService assets, Microphone microphone,
            VoiceProperties properties, RecognizerOpener opener, ScheduledExecutorService scheduler) {
        this.devices = devices;
        this.assets = assets;
        this.microphone = microphone;
        this.properties = properties;
        this.opener = opener;
        this.recognizers = new WarmRecognizers(scheduler, properties.idleShutdown());
    }

    public DictationSession start(DictationRequest request, DictationListener listener) {
        var lease = acquire(plan(request), request);
        ExecutorService executor = Executors.newSingleThreadExecutor(VoiceInputService::daemon);
        try {
            var dictation = new Dictation(lease.recognizer(), new SpeechGate(properties.speechRms()), properties,
                    languageOf(request), listener, executor);
            var input = microphone.open(request.input(), dictation::feed);
            return new DictationSession(input, dictation, executor, lease::release);
        } catch (RuntimeException e) {
            executor.shutdown();
            lease.release();
            throw e;
        }
    }

    public void install(DictationRequest request, DoubleConsumer progress) throws IOException {
        for (var asset : requiredAssets(plan(request))) {
            assets.install(asset, progress);
        }
    }

    public void warmUp(DictationRequest request) {
        acquire(plan(request), request).release();
    }

    @PreDestroy
    public void shutdown() {
        recognizers.shutdown();
    }

    private WarmRecognizers.Lease acquire(Plan plan, DictationRequest request) {
        requireInstalled(plan);
        var runtime = runtimeFor(plan.build());
        var model = path(request.model().asset());
        var vad = path(VoiceAsset.SILERO_VAD);
        return recognizers.acquire(plan.key(),
                () -> opener.open(runtime.build(), runtime.directory(), model, vad, plan.device()));
    }

    private Plan plan(DictationRequest request) {
        var spec = request.deviceSpec() == null || request.deviceSpec().isBlank() ? DEFAULT_DEVICE : request.deviceSpec();
        var selected = devices.select(spec);
        var device = devices.effectiveDevice(selected);
        return new Plan(new WarmRecognizers.Key(request.model(), device.id()),
                devices.buildFor(selected), device);
    }

    private String languageOf(DictationRequest request) {
        return request.language() == null || request.language().isBlank() ? properties.language() : request.language();
    }

    private List<VoiceAsset> requiredAssets(Plan plan) {
        return List.of(plan.build().asset(), plan.key().model().asset(), VoiceAsset.SILERO_VAD);
    }

    private void requireInstalled(Plan plan) {
        var missing = requiredAssets(plan).stream().filter(asset -> assets.pathOf(asset).isEmpty())
                .map(VoiceAsset::name).toList();
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Voice input is not installed, missing: " + String.join(", ", missing)
                    + ". Call install(...) first to download it.");
        }
    }

    /** True when the request needs another whisper runtime than the one this process loaded; {@link #start} fails then. */
    public boolean restartRequired(DictationRequest request) {
        var requested = plan(request).build();
        return libraryDirectory(requested).map(wanted -> servingRuntime(requested, wanted).isEmpty()).orElse(false);
    }

    private Runtime runtimeFor(RuntimeBuild requested) {
        var wanted = libraryDirectory(requested).orElseThrow();
        return complete(servingRuntime(requested, wanted).orElseThrow(() -> new IllegalStateException(
                "Another whisper runtime is already loaded in this process, restart the application to use "
                        + requested.asset().name())));
    }

    /** The loaded runtime wins: a CPU request is served by an already loaded CUDA runtime, anything else needs a restart. */
    private Optional<Runtime> servingRuntime(RuntimeBuild requested, Path wanted) {
        var loaded = opener.loadedDirectory();
        if (loaded.isEmpty() || loaded.get().equals(wanted)) {
            return Optional.of(new Runtime(requested, wanted));
        }
        var loadedBuild = RuntimeBuild.forCurrentPlatform().stream()
                .filter(build -> libraryDirectory(build).filter(loaded.get()::equals).isPresent()).findFirst();
        if (requested.flavor() == RuntimeBuild.Flavor.CPU && loadedBuild.isPresent()
                && loadedBuild.get().flavor() == RuntimeBuild.Flavor.CUDA) {
            return Optional.of(new Runtime(loadedBuild.get(), loaded.get()));
        }
        return Optional.empty();
    }

    private Runtime complete(Runtime runtime) {
        if (!runtime.build().isCompleteIn(runtime.directory())) {
            throw new IllegalStateException(runtime.build().asset().name() + " is incomplete in " + runtime.directory()
                    + ", install it again");
        }
        return runtime;
    }

    private Optional<Path> libraryDirectory(RuntimeBuild build) {
        return assets.pathOf(build.asset()).map(installed -> build.libraryDirectory(installed).toAbsolutePath().normalize());
    }

    private Path path(VoiceAsset asset) {
        return assets.pathOf(asset).orElseThrow(() -> new IllegalStateException(asset.name() + " is not installed"));
    }

    private static Thread daemon(Runnable runnable) {
        var thread = new Thread(runnable, "voice-worker");
        thread.setDaemon(true);
        return thread;
    }
}
