package com.github.krpkbhdn.ccmanager.voice;

import com.github.krpkbhdn.ccmanager.voice.asset.WhisperModel;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/**
 * Recognizers with their model open in VRAM/RAM, one per key and shared by its sessions. The last release starts
 * an idle timer that closes the recognizer. Opening happens outside the lock; the idle close runs inside it so a
 * new recognizer never opens while the old one is still closing. Shutdown closes live recognizers, so their
 * sessions see one "closed" error from the recognizer.
 */
final class WarmRecognizers {

    private static final String SHUT_DOWN = "Voice input is shut down";

    record Key(WhisperModel model, String deviceId) {}

    /** One session's hold on a warm recognizer; releasing twice counts once. */
    final class Lease {
        private final Entry entry;
        private final AtomicBoolean released = new AtomicBoolean();

        private Lease(Entry entry) {
            this.entry = entry;
        }

        Recognizer recognizer() {
            return entry.recognizer.join();
        }

        void release() {
            if (released.compareAndSet(false, true)) {
                WarmRecognizers.this.release(entry);
            }
        }
    }

    private static final class Entry {
        private final Key key;
        private final CompletableFuture<Recognizer> recognizer = new CompletableFuture<>();
        private int sessions;
        private ScheduledFuture<?> idleStop;

        private Entry(Key key) {
            this.key = key;
        }

        private void closeIfOpened() {
            if (recognizer.isDone() && !recognizer.isCompletedExceptionally()) {
                recognizer.join().close();
            }
        }
    }

    private final ScheduledExecutorService scheduler;
    private final Duration idleShutdown;
    private final Map<Key, Entry> warm = new HashMap<>();
    private boolean closed;

    WarmRecognizers(ScheduledExecutorService scheduler, Duration idleShutdown) {
        this.scheduler = scheduler;
        this.idleShutdown = idleShutdown;
    }

    /** Reuses the warm recognizer of the key or opens one with {@code opener}; blocks until it is open. */
    Lease acquire(Key key, Supplier<Recognizer> opener) {
        Entry entry;
        boolean opening = false;
        synchronized (this) {
            requireOpen();
            entry = warm.get(key);
            if (entry == null) {
                entry = new Entry(key);
                warm.put(key, entry);
                opening = true;
            }
            if (entry.idleStop != null) {
                entry.idleStop.cancel(false);
                entry.idleStop = null;
            }
            entry.sessions++;
        }
        if (opening) {
            open(entry, opener);
        }
        try {
            entry.recognizer.join();
        } catch (CompletionException e) {
            switch (e.getCause()) {
                case RuntimeException cause -> throw cause;
                case Error cause -> throw cause;
                default -> throw e;
            }
        }
        return new Lease(entry);
    }

    void shutdown() {
        List<Entry> stopped;
        synchronized (this) {
            closed = true;
            stopped = List.copyOf(warm.values());
            warm.clear();
        }
        scheduler.shutdownNow();
        RuntimeException failure = null;
        for (var entry : stopped) {
            try {
                entry.closeIfOpened();
            } catch (RuntimeException e) {
                if (failure == null) {
                    failure = e;
                } else {
                    failure.addSuppressed(e);
                }
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private void open(Entry entry, Supplier<Recognizer> opener) {
        try {
            var recognizer = opener.get();
            synchronized (this) {
                if (closed) {
                    recognizer.close();
                    throw new IllegalStateException(SHUT_DOWN);
                }
            }
            entry.recognizer.complete(recognizer);
        } catch (Throwable e) {
            synchronized (this) {
                warm.remove(entry.key, entry);
            }
            entry.recognizer.completeExceptionally(e);
            if (e instanceof Error error) {
                throw error;
            }
        }
    }

    private void requireOpen() {
        if (closed) {
            throw new IllegalStateException(SHUT_DOWN);
        }
    }

    private synchronized void release(Entry entry) {
        if (closed || --entry.sessions > 0 || warm.get(entry.key) != entry) {
            return;
        }
        try {
            entry.idleStop = scheduler.schedule(() -> closeIfIdle(entry), idleShutdown.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            // shutdown raced with this release and closes the recognizer itself
        }
    }

    private synchronized void closeIfIdle(Entry entry) {
        if (closed || entry.sessions != 0 || warm.get(entry.key) != entry) {
            return;
        }
        warm.remove(entry.key);
        entry.closeIfOpened();
    }
}
