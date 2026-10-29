package com.github.krpkbhdn.ccmanager.voice.audio;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Pcm16Test {

    @Test
    void convertsLittleEndianSignedSamples() {
        byte[] pcm = {0x00, 0x00, (byte) 0xFF, 0x7F, 0x00, (byte) 0x80, 0x00, 0x40};

        assertThat(Pcm16.toFloats(pcm, pcm.length))
                .containsExactly(0f, 32767f / 32768f, -1f, 0.5f);
    }

    @Test
    void dropsTrailingByteOfOddLength() {
        byte[] pcm = {0x00, 0x40, 0x12};

        assertThat(Pcm16.toFloats(pcm, 3)).containsExactly(0.5f);
    }

    @Test
    void readsOnlyGivenLength() {
        byte[] pcm = {0x00, 0x40, 0x00, 0x40};

        assertThat(Pcm16.toFloats(pcm, 2)).hasSize(1);
    }
}
