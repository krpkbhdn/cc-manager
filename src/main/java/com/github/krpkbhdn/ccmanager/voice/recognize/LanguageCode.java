package com.github.krpkbhdn.ccmanager.voice.recognize;

import java.util.Set;

/** The language codes whisper.cpp v1.9.2 knows (g_lang in whisper.cpp) plus {@code auto}; an unknown one makes whisper_lang_id return -1. */
public final class LanguageCode {

    public static final String AUTO = "auto";

    private static final Set<String> KNOWN = Set.of(AUTO,
            "en", "zh", "de", "es", "ru", "ko", "fr", "ja", "pt", "tr", "pl", "ca", "nl", "ar", "sv", "it", "id", "hi",
            "fi", "vi", "he", "uk", "el", "ms", "cs", "ro", "da", "hu", "ta", "no", "th", "ur", "hr", "bg", "lt", "la",
            "mi", "ml", "cy", "sk", "te", "fa", "lv", "bn", "sr", "az", "sl", "kn", "et", "mk", "br", "eu", "is", "hy",
            "ne", "mn", "bs", "kk", "sq", "sw", "gl", "mr", "pa", "si", "km", "sn", "yo", "so", "af", "oc", "ka", "be",
            "tg", "sd", "gu", "am", "yi", "lo", "uz", "fo", "ht", "ps", "tk", "nn", "mt", "sa", "lb", "my", "bo", "tl",
            "mg", "as", "tt", "haw", "ln", "ha", "ba", "jw", "su", "yue");

    private LanguageCode() {}

    public static boolean isKnown(String language) {
        return language != null && KNOWN.contains(language);
    }

    /** @throws IllegalArgumentException when {@code language} is not {@code auto} or a code whisper knows */
    public static String validate(String language) {
        if (!isKnown(language)) {
            throw new IllegalArgumentException("Unsupported language \"" + language
                    + "\"; use \"auto\" or a whisper code such as en, uk, de, pl, fr");
        }
        return language;
    }
}
