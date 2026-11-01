package com.github.krpkbhdn.ccmanager.voice;

import com.github.krpkbhdn.ccmanager.voice.stream.Dictation;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** A running dictation; closing stops the microphone, flushes the last utterance and releases the warm recognizer. */
public final class DictationSession implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(DictationSession.class);
    private static final Duration FLUSH_WAIT = Duration.ofSeconds(2);

    private final AutoCloseable microphone;
    private final Dictation dictation;
    private final ExecutorService recognitionExecutor;
    private final Runnable releaseRecognizer;
    private final AtomicBoolean closed = new AtomicBoolean();

    DictationSession(AutoCloseable microphone, Dictation dictation, ExecutorService recognitionExecutor,
            Runnable releaseRecognizer) {
        this.microphone = microphone;
        this.dictation = dictation;
        this.recognitionExecutor = recognitionExecutor;
        this.releaseRecognizer = releaseRecognizer;
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            microphone.close();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("Closing the microphone failed: {}", e.toString());
        } finally {
            flushThenRelease();
        }
    }

    private void flushThenRelease() {
        try {
            dictation.stop();
            recognitionExecutor.shutdown();
            if (!recognitionExecutor.awaitTermination(FLUSH_WAIT.toMillis(), TimeUnit.MILLISECONDS)) {
                log.warn("The last utterance was still recognizing after {} ms", FLUSH_WAIT.toMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            releaseRecognizer.run();
        }
    }
}
