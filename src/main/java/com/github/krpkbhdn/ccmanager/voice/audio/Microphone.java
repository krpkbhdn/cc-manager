package com.github.krpkbhdn.ccmanager.voice.audio;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;
import org.springframework.stereotype.Component;

/** Captures 16 kHz mono 16-bit audio from a chosen input device in 100 ms blocks. */
@Component
public class Microphone {

    private static final AudioFormat FORMAT = new AudioFormat(16000f, 16, 1, true, false);
    private static final DataLine.Info INFO = new DataLine.Info(TargetDataLine.class, FORMAT);
    private static final int BLOCK_BYTES = 3200;
    private static final long STOP_WAIT_MILLIS = 1000;

    public List<String> inputs() {
        var names = new ArrayList<String>();
        for (var info : AudioSystem.getMixerInfo()) {
            if (AudioSystem.getMixer(info).isLineSupported(INFO)) {
                names.add(info.getName());
            }
        }
        return names;
    }

    public AutoCloseable open(String input, Consumer<float[]> onBlock) {
        var line = openLine(input);
        var thread = new Thread(() -> pump(line, onBlock), "voice-microphone");
        thread.setDaemon(true);
        thread.start();
        return () -> {
            line.stop();
            line.close();
            thread.join(STOP_WAIT_MILLIS);
        };
    }

    private static TargetDataLine openLine(String input) {
        try {
            var line = (TargetDataLine) lineFor(input);
            line.open(FORMAT);
            try {
                line.start();
            } catch (RuntimeException e) {
                line.close();
                throw e;
            }
            return line;
        } catch (LineUnavailableException | IllegalArgumentException e) {
            throw new IllegalStateException("No usable microphone: " + e.getMessage(), e);
        }
    }

    private static javax.sound.sampled.Line lineFor(String input) throws LineUnavailableException {
        if (input == null || input.isBlank()) {
            if (!AudioSystem.isLineSupported(INFO)) {
                throw new IllegalStateException("No microphone found that records 16 kHz 16-bit mono");
            }
            return AudioSystem.getLine(INFO);
        }
        for (var info : AudioSystem.getMixerInfo()) {
            var mixer = AudioSystem.getMixer(info);
            if (info.getName().equals(input) && mixer.isLineSupported(INFO)) {
                return mixer.getLine(INFO);
            }
        }
        throw new IllegalStateException("Microphone not available: " + input);
    }

    private static void pump(TargetDataLine line, Consumer<float[]> onBlock) {
        var buffer = new byte[BLOCK_BYTES];
        while (line.isOpen()) {
            int read = line.read(buffer, 0, buffer.length);
            if (read > 0) {
                onBlock.accept(Pcm16.toFloats(buffer, read));
            } else if (read < 0) {
                return;
            }
        }
    }
}
