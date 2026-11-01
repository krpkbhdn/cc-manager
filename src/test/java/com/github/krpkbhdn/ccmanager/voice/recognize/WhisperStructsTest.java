package com.github.krpkbhdn.ccmanager.voice.recognize;

import static java.lang.foreign.ValueLayout.JAVA_BOOLEAN;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.foreign.Arena;
import java.lang.foreign.MemoryLayout.PathElement;
import java.lang.foreign.PaddingLayout;
import java.lang.foreign.StructLayout;
import org.junit.jupiter.api.Test;

class WhisperStructsTest {

    @Test
    void layoutsMatchTheCStructSizesOfV192() {
        assertThat(WhisperStructs.CONTEXT_PARAMS.byteSize()).isEqualTo(48);
        assertThat(WhisperStructs.VAD_CONTEXT_PARAMS.byteSize()).isEqualTo(12);
        assertThat(WhisperStructs.VAD_PARAMS.byteSize()).isEqualTo(24);
        assertThat(WhisperStructs.FULL_PARAMS.byteSize()).isEqualTo(304);
    }

    @Test
    void fullParamsFieldsSitWhereACompilerPutsThem() {
        assertThat(offsetOf("n_threads")).isEqualTo(4);
        assertThat(offsetOf("translate")).isEqualTo(20);
        assertThat(offsetOf("thold_pt")).isEqualTo(32);
        assertThat(offsetOf("suppress_regex")).isEqualTo(64);
        assertThat(offsetOf("language")).isEqualTo(104);
        assertThat(offsetOf("temperature")).isEqualTo(116);
        assertThat(offsetOf("new_segment_callback")).isEqualTo(160);
        assertThat(offsetOf("vad_model_path")).isEqualTo(272);
        assertThat(offsetOf("vad_params")).isEqualTo(280);
    }

    @Test
    void selfCheckAcceptsTheLayouts() {
        assertThatCode(LayoutSelfCheck::verifySizes).doesNotThrowAnyException();
    }

    @Test
    void structBuilderInsertsCPaddingAndRoundsUpToTheLargestAlignment() {
        StructLayout layout = new StructBuilder().add("flag", JAVA_BOOLEAN)
                .add("wide", JAVA_LONG).add("tail", JAVA_BOOLEAN).build();

        assertThat(layout.byteSize()).isEqualTo(24);
        assertThat(layout.byteOffset(PathElement.groupElement("wide"))).isEqualTo(8);
        assertThat(layout.memberLayouts()).filteredOn(PaddingLayout.class::isInstance).hasSize(2);
    }

    @Test
    void fullParamsMappingSetsThreadsLanguageAndQuietsWhisper() {
        try (var arena = Arena.ofConfined()) {
            var params = arena.allocate(WhisperStructs.FULL_PARAMS);
            var language = arena.allocateFrom("uk");

            FullParams.apply(params, 6, language);

            assertThat(WhisperStructs.get(params, WhisperStructs.FULL_PARAMS, "n_threads")).isEqualTo(6);
            assertThat(WhisperStructs.get(params, WhisperStructs.FULL_PARAMS, "language")).isEqualTo(language);
            assertThat(WhisperStructs.get(params, WhisperStructs.FULL_PARAMS, "no_context")).isEqualTo(true);
            assertThat(WhisperStructs.get(params, WhisperStructs.FULL_PARAMS, "suppress_nst")).isEqualTo(true);
            assertThat(WhisperStructs.get(params, WhisperStructs.FULL_PARAMS, "print_progress")).isEqualTo(false);
            assertThat(WhisperStructs.get(params, WhisperStructs.FULL_PARAMS, "vad")).isEqualTo(false);
        }
    }

    @Test
    void languageMustBeAutoOrACodeWhisperKnows() {
        for (var known : new String[] {"auto", "en", "uk", "yue", "haw"}) {
            assertThatCode(() -> LanguageCode.validate(known)).doesNotThrowAnyException();
        }
        for (var unknown : new String[] {"ua", "xx", "uk; rm", "", "UK"}) {
            assertThatThrownBy(() -> LanguageCode.validate(unknown)).isInstanceOf(IllegalArgumentException.class);
        }
        assertThatThrownBy(() -> LanguageCode.validate(null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void anUnknownLanguageIdYieldsNoCStringInsteadOfReadingAddressZero() {
        assertThat(GgmlDevices.cString(java.lang.foreign.MemorySegment.NULL)).isEmpty();
    }

    private static long offsetOf(String field) {
        return WhisperStructs.FULL_PARAMS.byteOffset(PathElement.groupElement(field));
    }
}
