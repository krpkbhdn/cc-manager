package com.github.krpkbhdn.ccmanager.voice.recognize;

import java.io.IOException;

/** Turns 16 kHz mono samples into text; {@code language} is a whisper code or "auto". */
public interface Recognizer extends AutoCloseable {

    Transcript transcribe(float[] samples, String language) throws IOException;

    @Override
    void close();
}
