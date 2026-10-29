package com.github.krpkbhdn.ccmanager.voice.asset;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Whisper ggml models by short alias, pinned to a commit of the ggerganov/whisper.cpp Hugging Face repository. */
public enum WhisperModel {
    BASE_Q5_1("base-q5_1", "ggml-base-q5_1.bin", "422f1ae452ade6f30a004d7e5c6a43195e4433bc370bf23fac9cc591f01a8898", 59707625L),
    SMALL_Q5_1("small-q5_1", "ggml-small-q5_1.bin", "ae85e4a935d7a567bd102fe55afc16bb595bdb618e11b2fc7591bc08120411bb", 190085487L),
    SMALL_Q8_0("small-q8_0", "ggml-small-q8_0.bin", "49c8fb02b65e6049d5fa6c04f81f53b867b5ec9540406812c643f177317f779f", 264464607L),
    LARGE_V3_TURBO_Q5_0("large-v3-turbo-q5_0", "ggml-large-v3-turbo-q5_0.bin", "394221709cd5ad1f40c46e6031ca61bce88931e6e088c188294c6d5a55ffa7e2", 574041195L),
    LARGE_V3_TURBO_Q8_0("large-v3-turbo-q8_0", "ggml-large-v3-turbo-q8_0.bin", "317eb69c11673c9de1e1f0d459b253999804ec71ac4c23c17ecf5fbe24e259a1", 874188075L),
    LARGE_V3_TURBO("large-v3-turbo", "ggml-large-v3-turbo.bin", "1fc70f774d38eb169993ac391eea357ef47c88757ef72ee5943879b7e8e2bc69", 1624555275L),
    LARGE_V3_Q5_0("large-v3-q5_0", "ggml-large-v3-q5_0.bin", "d75795ecff3f83b5faa89d1900604ad8c780abd5739fae406de19f23ecd98ad1", 1081140203L),
    LARGE_V3("large-v3", "ggml-large-v3.bin", "64d182b440b98d5203c4f9bd541544d84c605196c4f7b845dfa11fb23594d1e2", 3095033483L);

    private static final String BASE_URL =
            "https://huggingface.co/ggerganov/whisper.cpp/resolve/5359861c739e955e79d9a303bcbc70fb988958b1/";

    private static final Map<String, String> RENAMED_ALIASES = Map.ofEntries(
            Map.entry("base", "base-q5_1"),
            Map.entry("small", "small-q5_1"),
            Map.entry("small-q8", "small-q8_0"),
            Map.entry("medium-q5", "large-v3-turbo-q5_0"),
            Map.entry("medium-q5_0", "large-v3-turbo-q5_0"),
            Map.entry("turbo", "large-v3-turbo-q5_0"),
            Map.entry("turbo-q5_0", "large-v3-turbo-q5_0"),
            Map.entry("turbo-q8", "large-v3-turbo-q8_0"),
            Map.entry("turbo-q8_0", "large-v3-turbo-q8_0"),
            Map.entry("turbo-full", "large-v3-turbo"),
            Map.entry("large-v3-q5", "large-v3-q5_0"));

    private final String alias;
    private final String displayName;
    private final VoiceAsset asset;

    WhisperModel(String alias, String file, String sha256, long sizeBytes) {
        this.alias = alias;
        this.displayName = displayNameOf(file);
        this.asset = new VoiceAsset("Whisper " + displayName, BASE_URL + file, sha256, sizeBytes, "models/" + file, false);
    }

    /** Stored in user settings; never rename. */
    public String alias() {
        return alias;
    }

    /** Size name from the file, plus the quantization level when the file is quantized; the full model has no suffix. */
    public String displayName() {
        return displayName;
    }

    private static String displayNameOf(String file) {
        Matcher parts = Pattern.compile("ggml-(.+?)(?:-(q\\d_\\d))?\\.bin").matcher(file); // enum constants build before static fields exist
        if (!parts.matches()) {
            throw new IllegalArgumentException("Unexpected model file " + file);
        }
        String size = switch (parts.group(1)) {
            case "large-v3-turbo" -> "Large v3 Turbo";
            case "large-v3" -> "Large v3";
            default -> Character.toUpperCase(parts.group(1).charAt(0)) + parts.group(1).substring(1);
        };
        return parts.group(2) == null ? size : size + " " + parts.group(2).toUpperCase(Locale.ROOT);
    }

    public VoiceAsset asset() {
        return asset;
    }

    /** Resolves a stored alias; aliases from before quantization levels were named (a plain "turbo" was Q5) are mapped to the same file. */
    public static Optional<WhisperModel> byAlias(String alias) {
        String current = RENAMED_ALIASES.getOrDefault(alias, alias);
        return Arrays.stream(values()).filter(model -> model.alias.equals(current)).findFirst();
    }
}
