package com.github.krpkbhdn.ccmanager.voice;

import com.github.krpkbhdn.ccmanager.voice.asset.RuntimeBuild;
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import com.github.krpkbhdn.ccmanager.voice.recognize.FfmRecognizer;
import com.github.krpkbhdn.ccmanager.voice.recognize.NativeRuntime;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import java.nio.file.Path;
import java.util.Optional;

/** The seam to the process-wide native runtime: which directory is loaded and how a model gets opened. */
interface RecognizerOpener {

    Optional<Path> loadedDirectory();

    Recognizer open(RuntimeBuild build, Path libraryDirectory, Path model, Path vad, ComputeDevice device);

    static RecognizerOpener ffm(VoiceProperties properties) {
        return new RecognizerOpener() {
            @Override
            public Optional<Path> loadedDirectory() {
                return NativeRuntime.loaded().map(NativeRuntime::directory);
            }

            @Override
            public Recognizer open(RuntimeBuild build, Path libraryDirectory, Path model, Path vad,
                    ComputeDevice device) {
                var runtime = NativeRuntime.load(libraryDirectory, build.libraries());
                return FfmRecognizer.open(runtime, model, vad, device, properties);
            }
        };
    }
}
