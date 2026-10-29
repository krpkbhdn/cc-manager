package com.github.krpkbhdn.ccmanager.voice.audio;

/** Converts little-endian signed 16-bit PCM to normalized floats. */
public final class Pcm16 {

    private static final float SCALE = 32768f;

    private Pcm16() {
    }

    public static float[] toFloats(byte[] pcm, int length) {
        var samples = new float[length / 2];
        for (int i = 0; i < samples.length; i++) {
            short value = (short) ((pcm[2 * i] & 0xFF) | (pcm[2 * i + 1] << 8));
            samples[i] = value / SCALE;
        }
        return samples;
    }
}
