package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SegmentAllocator;
import java.lang.invoke.MethodHandle;
import java.nio.file.Path;

/** The whisper_* functions the recognizer needs, each wrapped in a typed method. Struct params go by value, as in C. */
final class WhisperLibrary {

    private static final String FAILED = "whisper call";

    private final MethodHandle contextDefaults;
    private final MethodHandle initFromFile;
    private final MethodHandle freeContext;
    private final MethodHandle fullDefaults;
    private final MethodHandle fullDefaultsByRef;
    private final MethodHandle freeParams;
    private final MethodHandle full;
    private final MethodHandle segmentCount;
    private final MethodHandle segmentText;
    private final MethodHandle noSpeechProbability;
    private final MethodHandle languageId;
    private final MethodHandle languageName;

    WhisperLibrary(NativeRuntime runtime) {
        var contextParams = WhisperStructs.CONTEXT_PARAMS;
        var fullParams = WhisperStructs.FULL_PARAMS;
        contextDefaults = runtime.handle("whisper_context_default_params", FunctionDescriptor.of(contextParams));
        initFromFile = runtime.handle("whisper_init_from_file_with_params", FunctionDescriptor.of(ADDRESS, ADDRESS, contextParams));
        freeContext = runtime.handle("whisper_free", FunctionDescriptor.ofVoid(ADDRESS));
        fullDefaults = runtime.handle("whisper_full_default_params", FunctionDescriptor.of(fullParams, JAVA_INT));
        fullDefaultsByRef = runtime.handle("whisper_full_default_params_by_ref", FunctionDescriptor.of(ADDRESS, JAVA_INT));
        freeParams = runtime.handle("whisper_free_params", FunctionDescriptor.ofVoid(ADDRESS));
        full = runtime.handle("whisper_full",FunctionDescriptor.of(JAVA_INT, ADDRESS, fullParams, ADDRESS, JAVA_INT));
        segmentCount = runtime.handle("whisper_full_n_segments", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        segmentText = runtime.handle("whisper_full_get_segment_text", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT));
        noSpeechProbability = runtime.handle("whisper_full_get_segment_no_speech_prob", FunctionDescriptor.of(JAVA_FLOAT, ADDRESS, JAVA_INT));
        languageId = runtime.handle("whisper_full_lang_id", FunctionDescriptor.of(JAVA_INT, ADDRESS));
        languageName = runtime.handle("whisper_lang_str", FunctionDescriptor.of(ADDRESS, JAVA_INT));
    }

    MemorySegment contextDefaults(SegmentAllocator allocator) {
        try {
            return (MemorySegment) contextDefaults.invoke(allocator);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    /** The context pointer, or NULL when the model cannot be loaded. */
    MemorySegment initContext(Path model, MemorySegment params) {
        try (var call = Arena.ofConfined()) {
            return (MemorySegment) initFromFile.invoke(call.allocateFrom(model.toAbsolutePath().toString()), params);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    void freeContext(MemorySegment context) {
        try {
            freeContext.invoke(context);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    MemorySegment fullDefaults(SegmentAllocator allocator, int strategy) {
        try {
            return (MemorySegment) fullDefaults.invoke(allocator, strategy);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    /** A view of the dll's heap copy of the defaults, for the layout self-check; release it with {@link #freeParams}. */
    MemorySegment fullDefaultsByRef(int strategy) {
        try {
            return ((MemorySegment) fullDefaultsByRef.invoke(strategy)).reinterpret(WhisperStructs.FULL_PARAMS.byteSize());
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    void freeParams(MemorySegment params) {
        try {
            freeParams.invoke(params);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    int full(MemorySegment context, MemorySegment params, MemorySegment samples, int sampleCount) {
        try {
            return (int) full.invoke(context, params, samples, sampleCount);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    int segmentCount(MemorySegment context) {
        try {
            return (int) segmentCount.invoke(context);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    String segmentText(MemorySegment context, int segment) {
        try {
            return GgmlDevices.cString((MemorySegment) segmentText.invoke(context, segment));
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    float noSpeechProbability(MemorySegment context, int segment) {
        try {
            return (float) noSpeechProbability.invoke(context, segment);
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }

    /** The detected language code, "" when whisper reports none (id -1). */
    String detectedLanguage(MemorySegment context) {
        try {
            int id = (int) languageId.invoke(context);
            return id < 0 ? "" : GgmlDevices.cString((MemorySegment) languageName.invoke(id));
        } catch (Throwable e) {
            throw NativeFailure.of(FAILED, e);
        }
    }
}
