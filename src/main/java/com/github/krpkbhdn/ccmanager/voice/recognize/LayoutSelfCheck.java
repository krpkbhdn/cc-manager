package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.StructLayout;

/**
 * Proves the struct layouts match the loaded dll before the first by-value call: a wrong size or offset
 * would otherwise corrupt the stack or jump into data at the first {@code whisper_full}. Sizes and offsets
 * are those of a C compiler on a 64-bit target for whisper.h v1.9.2.
 */
final class LayoutSelfCheck {

    private static boolean verified;

    private static final int DEFAULT_BEAM_SIZE = 5;
    private static final float DEFAULT_VAD_THRESHOLD = 0.5f;
    private static final int CONTEXT_PARAMS_BYTES = 48;
    private static final int VAD_CONTEXT_PARAMS_BYTES = 12;
    private static final int VAD_PARAMS_BYTES = 24;
    private static final int FULL_PARAMS_BYTES = 304;
    private static final int LANGUAGE_OFFSET = 104;
    private static final int VAD_MODEL_PATH_OFFSET = 272;
    private static final int VAD_PARAMS_OFFSET = 280;
    private static final int BEAM_SIZE_OFFSET = 148;

    private LayoutSelfCheck() {}

    /** Layout against the C sizes; needs no dll. */
    static void verifySizes() {
        size("whisper_context_params", WhisperStructs.CONTEXT_PARAMS, CONTEXT_PARAMS_BYTES);
        size("whisper_vad_context_params", WhisperStructs.VAD_CONTEXT_PARAMS, VAD_CONTEXT_PARAMS_BYTES);
        size("whisper_vad_params", WhisperStructs.VAD_PARAMS, VAD_PARAMS_BYTES);
        size("whisper_full_params", WhisperStructs.FULL_PARAMS, FULL_PARAMS_BYTES);
        offset("language", LANGUAGE_OFFSET);
        offset("vad_model_path", VAD_MODEL_PATH_OFFSET);
        offset("vad_params", VAD_PARAMS_OFFSET);
        long beamSize = WhisperStructs.FULL_PARAMS.byteOffset(PathElement.groupElement("beam_search"), PathElement.groupElement("beam_size"));
        if (beamSize != BEAM_SIZE_OFFSET) {
            throw new IllegalStateException("whisper_full_params.beam_search.beam_size is at byte " + beamSize
                    + ", whisper.h v1.9.2 has " + BEAM_SIZE_OFFSET);
        }
    }

    /**
     * Once per process (the runtime is one per process too): sizes, then for each strategy the dll's by-ref
     * defaults at the expected offsets, and only then the by-value call compared field by field with them.
     * The context and VAD structs have no by-ref default functions in v1.9.2, so only their size is checked.
     */
    static synchronized void verify(WhisperLibrary whisper) {
        if (verified) {
            return;
        }
        verifySizes();
        for (int strategy : new int[] {FullParams.STRATEGY_GREEDY, FullParams.STRATEGY_BEAM_SEARCH}) {
            verifyDefaults(whisper, strategy);
        }
        verified = true;
    }

    private static void verifyDefaults(WhisperLibrary whisper, int strategy) {
        var reference = whisper.fullDefaultsByRef(strategy);
        try (var arena = Arena.ofConfined()) {
            float vadThreshold = reference.get(JAVA_FLOAT, VAD_PARAMS_OFFSET);
            int beamSize = reference.get(JAVA_INT, BEAM_SIZE_OFFSET);
            boolean beamOk = strategy != FullParams.STRATEGY_BEAM_SEARCH || beamSize == DEFAULT_BEAM_SIZE;
            if (vadThreshold != DEFAULT_VAD_THRESHOLD || !beamOk) {
                throw mismatch(strategy, -1);
            }
            int differing = firstDifferingField(whisper.fullDefaults(arena, strategy), reference);
            if (differing >= 0) {
                throw mismatch(strategy, differing);
            }
        } finally {
            whisper.freeParams(reference);
        }
    }

    private static IllegalStateException mismatch(int strategy, int atByte) {
        return new IllegalStateException("whisper_full_params does not match the loaded whisper.dll (strategy " + strategy
                + ", first differing field at byte " + atByte + "); the dll is not v1.9.2");
    }

    private static int firstDifferingField(MemorySegment a, MemorySegment b) {
        long offset = 0;
        for (var member : WhisperStructs.FULL_PARAMS.memberLayouts()) {
            long size = member.byteSize();
            if (!(member instanceof PaddingLayout) && a.asSlice(offset, size).mismatch(b.asSlice(offset, size)) != -1) {
                return (int) offset;
            }
            offset += size;
        }
        return -1;
    }

    private static void size(String name, StructLayout layout, int expected) {
        if (layout.byteSize() != expected) {
            throw new IllegalStateException(name + " layout is " + layout.byteSize() + " bytes, whisper.h v1.9.2 has " + expected);
        }
    }

    private static void offset(String field, int expected) {
        long actual = WhisperStructs.FULL_PARAMS.byteOffset(PathElement.groupElement(field));
        if (actual != expected) {
            throw new IllegalStateException("whisper_full_params." + field + " is at byte " + actual + ", whisper.h v1.9.2 has " + expected);
        }
    }
}
