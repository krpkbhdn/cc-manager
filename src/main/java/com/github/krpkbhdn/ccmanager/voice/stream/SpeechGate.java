package com.github.krpkbhdn.ccmanager.voice.stream;

/** Energy-based check whether audio holds speech or ends in a pause. */
public final class SpeechGate {

    private final double speechRms;

    public SpeechGate(double speechRms) {
        this.speechRms = speechRms;
    }

    public boolean hasSpeech(float[] samples) {
        return rms(samples, 0, samples.length) >= speechRms;
    }

    public boolean endsInPause(float[] samples, int tailSamples) {
        return samples.length >= tailSamples && rms(samples, samples.length - tailSamples, samples.length) < speechRms;
    }

    private static double rms(float[] samples, int from, int to) {
        if (to <= from) {
            return 0;
        }
        double sum = 0;
        for (int i = from; i < to; i++) {
            sum += samples[i] * samples[i];
        }
        return Math.sqrt(sum / (to - from));
    }
}
