package com.github.krpkbhdn.ccmanager.voice.recognize;

import java.lang.foreign.MemorySegment;

/** Maps dictation settings onto a whisper_full_params segment obtained from whisper_full_default_params. */
final class FullParams {

    static final int STRATEGY_GREEDY = 0;
    static final int STRATEGY_BEAM_SEARCH = 1;

    private FullParams() {}

    /** {@code language} must stay allocated until whisper_full returns. */
    static void apply(MemorySegment params, int threads, MemorySegment language) {
        var layout = WhisperStructs.FULL_PARAMS;
        WhisperStructs.set(params, layout, "n_threads", threads);
        WhisperStructs.set(params, layout, "language", language);
        WhisperStructs.set(params, layout, "translate", false);
        WhisperStructs.set(params, layout, "no_context", true);
        WhisperStructs.set(params, layout, "no_timestamps", true);
        WhisperStructs.set(params, layout, "suppress_nst", true);
        WhisperStructs.set(params, layout, "print_progress", false);
        WhisperStructs.set(params, layout, "print_realtime", false);
        WhisperStructs.set(params, layout, "print_timestamps", false);
        WhisperStructs.set(params, layout, "print_special", false);
        WhisperStructs.set(params, layout, "vad", false);
    }
}
