package com.github.krpkbhdn.ccmanager.voice.recognize;

/** A GPU ggml found; {@code ordinal} counts GPU devices only and is what whisper's {@code gpu_device} means. */
public record GgmlGpu(int ordinal, String name, String description, boolean integrated) {}
