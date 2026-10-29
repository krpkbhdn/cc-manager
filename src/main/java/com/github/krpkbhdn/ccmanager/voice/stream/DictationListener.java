package com.github.krpkbhdn.ccmanager.voice.stream;

/** Receives dictation results: a changing partial text, then the final text of each utterance. */
public interface DictationListener {

    void onPartial(String text);

    void onFinal(String text);

    void onError(Throwable error);
}
