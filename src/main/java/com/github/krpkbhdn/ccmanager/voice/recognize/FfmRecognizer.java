package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;

import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** whisper.cpp inside this process through java.lang.foreign: one context, calls serialized, nothing spawned. */
public final class FfmRecognizer implements Recognizer {

    private static final Logger log =LoggerFactory.getLogger(FfmRecognizer.class);
    private static final Pattern SERVICE_TAG = Pattern.compile("\\s*(\\[[^\\]]*]|\\([^)]*\\))\\s*");
    private static final float NO_SPEECH_LIMIT = 0.6f;

    private final WhisperLibrary whisper;
    private final SileroVad vad;
    private final MemorySegment context;
    private final int threads;
    private boolean closed;

    /** Loads the model onto {@code device} (the CPU when that GPU is not exposed by the runtime) and the Silero VAD. */
    public static FfmRecognizer open(NativeRuntime runtime, Path model, Path vadModel, ComputeDevice device,
            VoiceProperties properties) {
        var whisper = new WhisperLibrary(runtime);
        LayoutSelfCheck.verify(whisper);
        var context = initContext(whisper, model, device, runtime);
        try {
            return new FfmRecognizer(whisper, context, new SileroVad(runtime, vadModel, (float) properties.vadThreshold()),
                    properties.threads());
        } catch (RuntimeException e) {
            whisper.freeContext(context);
            throw e;
        }
    }

    private FfmRecognizer(WhisperLibrary whisper, MemorySegment context, SileroVad vad, int threads) {
        this.whisper = whisper;
        this.context = context;
        this.vad = vad;
        this.threads = threads;
    }

    @Override
    public synchronized Transcript transcribe(float[] samples, String language) {
        LanguageCode.validate(language);
        if (closed) {
            throw new IllegalStateException("The recognizer is closed");
        }
        if (!vad.hasSpeech(samples)) {
            return new Transcript("", language);
        }
        try (var call = Arena.ofConfined()) {
            var params = whisper.fullDefaults(call, FullParams.STRATEGY_GREEDY);
            FullParams.apply(params, threads, call.allocateFrom(language));
            int code = whisper.full(context, params, call.allocateFrom(JAVA_FLOAT, samples), samples.length);
            if (code != 0) {
                throw new IllegalStateException("whisper_full failed with code " + code);
            }
            return readSegments();
        }
    }

    @Override
    public synchronized void close() {
        if (!closed) {
            closed = true;
            try {
                vad.close();
            } finally {
                whisper.freeContext(context);
            }
        }
    }

    private Transcript readSegments() {
        var text = new StringBuilder();
        for (int i = 0, count = whisper.segmentCount(context); i < count; i++) {
            if (whisper.noSpeechProbability(context, i) <= NO_SPEECH_LIMIT) {
                text.append(whisper.segmentText(context, i));
            }
        }
        var raw = text.toString();
        return new Transcript(SERVICE_TAG.matcher(raw).matches() ? "" : raw.trim(), whisper.detectedLanguage(context));
    }

    private static MemorySegment initContext(WhisperLibrary whisper, Path model, ComputeDevice device, NativeRuntime runtime) {
        var gpu = GpuSelection.ordinalFor(device, runtime.gpus());
        if (device.kind() == ComputeDevice.Kind.GPU && gpu.isEmpty()) {
            log.warn("GPU {} is not exposed by the loaded whisper runtime, using the CPU", device.label());
        }
        try (var arena = Arena.ofConfined()) {
            var params = whisper.contextDefaults(arena);
            WhisperStructs.set(params, WhisperStructs.CONTEXT_PARAMS, "use_gpu", gpu.isPresent());
            WhisperStructs.set(params, WhisperStructs.CONTEXT_PARAMS, "gpu_device", gpu.orElse(0));
            var context = whisper.initContext(model, params);
            if (context.equals(MemorySegment.NULL)) {
                throw new IllegalStateException("Cannot load the whisper model " + model);
            }
            return context;
        }
    }
}
