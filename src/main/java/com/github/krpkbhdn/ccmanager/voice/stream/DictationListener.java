package com.github.krpkbhdn.ccmanager.voice.stream;

/** Receives dictation results: a changing partial text, then the final text of each utterance. */
public interface DictationListener {

    void onPartial(String text);

    void onFinal(String text);

    void onError(Throwable error);

    /** The loudness of the latest audio block, 0 (silence) to 1; optional, called from the audio thread. */
    default void onLevel(double level) {
    }
}
