package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/** Silero VAD through whisper_vad_*: tells whether audio holds speech, so silence never reaches whisper_full. */
final class SileroVad implements AutoCloseable {

    private static final String FAILED = "whisper VAD call";

    private final MethodHandle defaultParams;
    private final MethodHandle segmentsFromSamples;
    private final MethodHandle segmentCount;
    private final MethodHandle freeSegments;
    private final MethodHandle free;
    private final float threshold;
    private final MemorySegment context;

    SileroVad(NativeRuntime runtime, Path model, float threshold) {
        this.threshold = threshold;
        var vadParams = WhisperStructs.VAD_PARAMS;
        var contextParams = WhisperStructs.VAD_CONTEXT_PARAMS;
        defaultParams = runtime.handle("whisper_vad_default_params", FunctionDescriptor.of(vadParams));
        segmentsFromSamples = runtime.handle("whisper_vad_segments_from_samples",
                FunctionDescriptor.of(ADDRESS, ADDRESS, vadParams, ADDRESS, JAVA_INT));
        segmentCount = runtime.handle("whisper_vad_segments_n_segments", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        freeSegments = runtime.handle("whisper_vad_free_segments", FunctionDescriptor.ofVoid(ADDRESS));
        free = runtime.handle("whisper_vad_free", FunctionDescriptor.ofVoid(ADDRESS));
        var contextDefaults = runtime.handle("whisper_vad_default_context_params", FunctionDescriptor.of(contextParams));
        var init = runtime.handle("whisper_vad_init_from_file_with_params", FunctionDescriptor.of(ADDRESS, ADDRESS, contextParams));
        try (var call = Arena.ofConfined()) {
            var params = (MemorySegment) contextDefaults.invoke(call);
            WhisperStructs.set(params, contextParams, "use_gpu", false);
            context = (MemorySegment) init.invoke(call.allocateFrom(model.toAbsolutePath().toString()), params);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
        if (context.equals(MemorySegment.NULL)) {
            throw new IllegalStateException("Cannot load the VAD model " + model);
        }
    }

    /** True when speech is found; also true when the VAD gives no answer, so speech is never dropped by mistake. */
    boolean hasSpeech(float[] samples) {
        try (var call = Arena.ofConfined()) {
            var params = (MemorySegment) defaultParams.invoke(call);
            WhisperStructs.set(params, WhisperStructs.VAD_PARAMS, "threshold", threshold);
            var segments = (MemorySegment) segmentsFromSamples.invoke(context, params,
                    call.allocateFrom(JAVA_FLOAT, samples), samples.length);
            if (segments.equals(MemorySegment.NULL)) {
                return true;
            }
            int count = (int) segmentCount.invoke(segments);
            freeSegments.invoke(segments);
            return count > 0;
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    @Override
    public void close() {
        try {
            free.invoke(context);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }
}
