package com.github.krpkbhdn.ccmanager.voice.stream;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SpeechGateTest {

    private final SpeechGate gate = new SpeechGate(0.01);

    @Test
    void silenceHasNoSpeech() {
        assertThat(gate.hasSpeech(new float[1600])).isFalse();
    }

    @Test
    void sineHasSpeech() {
        assertThat(gate.hasSpeech(sine(1600))).isTrue();
    }

    @Test
    void detectsPauseInTail() {
        var samples = new float[3200];
        System.arraycopy(sine(1600), 0, samples, 0, 1600);

        assertThat(gate.endsInPause(samples, 1600)).isTrue();
        assertThat(gate.endsInPause(sine(3200), 1600)).isFalse();
    }

    @Test
    void shortArrayIsNotPause() {
        assertThat(gate.endsInPause(new float[100], 1600)).isFalse();
    }

    private static float[] sine(int length) {
        var samples = new float[length];
        for (int i = 0; i < length; i++) {
            samples[i] = (float) (0.5 * Math.sin(2 * Math.PI * 440 * i / 16000));
        }
        return samples;
    }
}
