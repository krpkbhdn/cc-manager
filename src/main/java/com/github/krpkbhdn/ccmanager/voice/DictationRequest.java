package com.github.krpkbhdn.ccmanager.voice;

import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel;
import com.github.krpkbhdn.ccmanager.voice.recognize.LanguageCode;

/** What to dictate with; a null or blank deviceSpec means "best", input the system default microphone, language the configured one. */
public record DictationRequest(WhisperModel model, String deviceSpec, String input, String language) {

    public DictationRequest {
        if (model == null) {
            throw new IllegalArgumentException("A whisper model is required");
        }
        if (language != null && !language.isBlank()) {
            LanguageCode.validate(language);
        }
    }
}
