package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_LONG;

import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.StructLayout;
import java.util.Arrays;

/** Layouts of the whisper.h v1.9.2 structs passed by value; they must match the dll of the same tag (see {@link LayoutSelfCheck}). */
final class WhisperStructs {

    static final StructLayout CONTEXT_PARAMS = new StructBuilder()
            .add("use_gpu", JAVA_BOOLEAN)
            .add("flash_attn", JAVA_BOOLEAN)
            .add("gpu_device", JAVA_INT)
            .add("dtw_token_timestamps", JAVA_BOOLEAN)
            .add("dtw_aheads_preset", JAVA_INT)
            .add("dtw_n_top", JAVA_INT)
            .add("dtw_aheads", new StructBuilder().add("n_heads", JAVA_LONG).add("heads", ADDRESS))
            .add("dtw_mem_size", JAVA_LONG)
            .build();

    static final StructLayout VAD_CONTEXT_PARAMS = new StructBuilder()
            .add("n_threads", JAVA_INT)
            .add("use_gpu", JAVA_BOOLEAN)
            .add("gpu_device", JAVA_INT)
            .build();

    static final StructLayout VAD_PARAMS = vadParams().build();

    static final StructLayout FULL_PARAMS = new StructBuilder()
            .add("strategy", JAVA_INT)
            .add("n_threads", JAVA_INT)
            .add("n_max_text_ctx", JAVA_INT)
            .add("offset_ms", JAVA_INT)
            .add("duration_ms", JAVA_INT)
            .add("translate", JAVA_BOOLEAN)
            .add("no_context", JAVA_BOOLEAN)
            .add("no_timestamps", JAVA_BOOLEAN)
            .add("single_segment", JAVA_BOOLEAN)
            .add("print_special", JAVA_BOOLEAN)
            .add("print_progress", JAVA_BOOLEAN)
            .add("print_realtime", JAVA_BOOLEAN)
            .add("print_timestamps", JAVA_BOOLEAN)
            .add("token_timestamps", JAVA_BOOLEAN)
            .add("thold_pt", JAVA_FLOAT)
            .add("thold_ptsum", JAVA_FLOAT)
            .add("max_len", JAVA_INT)
            .add("split_on_word", JAVA_BOOLEAN)
            .add("max_tokens", JAVA_INT)
            .add("debug_mode", JAVA_BOOLEAN)
            .add("audio_ctx", JAVA_INT)
            .add("tdrz_enable", JAVA_BOOLEAN)
            .add("suppress_regex", ADDRESS)
            .add("initial_prompt", ADDRESS)
            .add("carry_initial_prompt", JAVA_BOOLEAN)
            .add("prompt_tokens", ADDRESS)
            .add("prompt_n_tokens", JAVA_INT)
            .add("language", ADDRESS)
            .add("detect_language", JAVA_BOOLEAN)
            .add("suppress_blank", JAVA_BOOLEAN)
            .add("suppress_nst", JAVA_BOOLEAN)
            .add("temperature", JAVA_FLOAT)
            .add("max_initial_ts", JAVA_FLOAT)
            .add("length_penalty", JAVA_FLOAT)
            .add("temperature_inc", JAVA_FLOAT)
            .add("entropy_thold", JAVA_FLOAT)
            .add("logprob_thold", JAVA_FLOAT)
            .add("no_speech_thold", JAVA_FLOAT)
            .add("greedy", new StructBuilder().add("best_of", JAVA_INT))
            .add("beam_search", new StructBuilder().add("beam_size", JAVA_INT).add("patience", JAVA_FLOAT))
            .add("new_segment_callback", ADDRESS)
            .add("new_segment_callback_user_data", ADDRESS)
            .add("progress_callback", ADDRESS)
            .add("progress_callback_user_data", ADDRESS)
            .add("encoder_begin_callback", ADDRESS)
            .add("encoder_begin_callback_user_data", ADDRESS)
            .add("abort_callback", ADDRESS)
            .add("abort_callback_user_data", ADDRESS)
            .add("logits_filter_callback", ADDRESS)
            .add("logits_filter_callback_user_data", ADDRESS)
            .add("grammar_rules", ADDRESS)
            .add("n_grammar_rules", JAVA_LONG)
            .add("i_start_rule", JAVA_LONG)
            .add("grammar_penalty", JAVA_FLOAT)
            .add("vad", JAVA_BOOLEAN)
            .add("vad_model_path", ADDRESS)
            .add("vad_params", vadParams())
            .build();

    private WhisperStructs() {}

    static Object get(MemorySegment struct, StructLayout layout, String... path) {
        return layout.varHandle(elements(path)).get(struct, 0L);
    }

    static void set(MemorySegment struct, StructLayout layout, String field, Object value) {
        layout.varHandle(PathElement.groupElement(field)).set(struct, 0L, value);
    }

    private static PathElement[] elements(String... path) {
        return Arrays.stream(path).map(PathElement::groupElement).toArray(PathElement[]::new);
    }

    private static StructBuilder vadParams() {
        return new StructBuilder()
                .add("threshold", JAVA_FLOAT)
                .add("min_speech_duration_ms", JAVA_INT)
                .add("min_silence_duration_ms", JAVA_INT)
                .add("max_speech_duration_s", JAVA_FLOAT)
                .add("speech_pad_ms", JAVA_INT)
                .add("samples_overlap", JAVA_FLOAT);
    }
}
