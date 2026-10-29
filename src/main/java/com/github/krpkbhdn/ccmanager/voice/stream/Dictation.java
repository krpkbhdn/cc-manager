package com.github.krpkbhdn.ccmanager.voice.stream;

import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import java.util.Arrays;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Pseudo-realtime dictation: re-transcribes a sliding audio window every tick and finalizes it
 * on a pause, a full window or {@link #stop()}. Ticks count samples, not wall-clock time.
 */
public final class Dictation {

    private static final int SAMPLE_RATE = 16_000;
    private static final int MILLIS_PER_SECOND = 1000;

    private final Recognizer recognizer;
    private final SpeechGate gate;
    private final String language;
    private final DictationListener listener;
    private final Executor recognitionExecutor;
    private final int tickSamples;
    private final int windowSamples;
    private final int pauseSamples;
    private final AtomicBoolean busy = new AtomicBoolean();
    private float[] window = new float[0];
    private int sinceTick;
    private boolean stopped;
    private String lastPartial = "";
    private volatile boolean failing;

    public Dictation(Recognizer recognizer, SpeechGate gate, VoiceProperties properties, String language,
            DictationListener listener, Executor recognitionExecutor) {
        this.recognizer = recognizer;
        this.gate = gate;
        this.language = language;
        this.listener = listener;
        this.recognitionExecutor = recognitionExecutor;
        this.tickSamples = samplesOf(properties.tick().toMillis());
        this.windowSamples = samplesOf(properties.window().toMillis());
        this.pauseSamples = samplesOf(properties.pause().toMillis());
    }

    public synchronized void feed(float[] block) {
        if (stopped) {
            return;
        }
        append(block);
        sinceTick += block.length;
        if (sinceTick >= tickSamples && busy.compareAndSet(false, true)) {
            sinceTick = 0;
            var snapshot = window.clone();
            recognitionExecutor.execute(() -> recognize(snapshot, false));
        }
    }

    public synchronized void stop() {
        if (stopped) {
            return;
        }
        stopped = true;
        recognitionExecutor.execute(this::flush);
    }

    private void flush() {
        var snapshot = currentWindow();
        if (snapshot.length > 0) {
            recognize(snapshot, true);
        }
    }

    private synchronized float[] currentWindow() {
        return window.clone();
    }

    /** Keeps only the newest window while no recognition is in flight, so repeated failures cannot grow it. */
    private void append(float[] block) {
        int total = window.length + block.length;
        int keep = busy.get() ? total : Math.min(total, windowSamples);
        var grown = new float[keep];
        int fromBlock = Math.min(block.length, keep);
        int fromWindow = keep - fromBlock;
        System.arraycopy(window, window.length - fromWindow, grown, 0, fromWindow);
        System.arraycopy(block, block.length - fromBlock, grown, fromWindow, fromBlock);
        window = grown;
    }

    private void recognize(float[] snapshot, boolean flush) {
        try {
            if (!gate.hasSpeech(snapshot)) {
                drop(snapshot.length);
                return;
            }
            var text = recognizer.transcribe(snapshot, language).text().strip();
            failing = false;
            boolean closed = flush || snapshot.length >= windowSamples || gate.endsInPause(snapshot, pauseSamples);
            if (closed) {
                drop(snapshot.length);
            }
            publish(text, closed);
        } catch (Throwable e) {
            reportFailure(e);
            if (e instanceof VirtualMachineError error) {
                throw error;
            }
        } finally {
            busy.set(false);
        }
    }

    private void reportFailure(Throwable e) {
        if (!failing) {
            failing = true;
            listener.onError(e);
        }
    }

    private void publish(String text, boolean closed) {
        if (closed) {
            lastPartial = "";
            if (!text.isEmpty()) {
                listener.onFinal(text);
            }
        } else if (!text.isEmpty() && !text.equals(lastPartial)) {
            lastPartial = text;
            listener.onPartial(text);
        }
    }

    private synchronized void drop(int samples) {
        window = Arrays.copyOfRange(window, Math.min(samples, window.length), window.length);
    }

    private static int samplesOf(long millis) {
        return (int) (SAMPLE_RATE * millis / MILLIS_PER_SECOND);
    }
}
