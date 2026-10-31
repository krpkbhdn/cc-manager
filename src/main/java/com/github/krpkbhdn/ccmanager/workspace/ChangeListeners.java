package com.github.krpkbhdn.ccmanager.workspace;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Change callbacks of a service: a failing listener is logged and never stops the others. Thread-safe. */
public final class ChangeListeners {

    private static final Logger log = LoggerFactory.getLogger(ChangeListeners.class);

    private final List<Runnable> listeners = new CopyOnWriteArrayList<>();
    private final String subject;

    public ChangeListeners(String subject) {
        this.subject = subject;
    }

    /** @return unsubscribe handle */
    public Runnable add(Runnable listener) {
        listeners.add(listener);
        return () -> listeners.remove(listener);
    }

    public void fire() {
        for (var listener : listeners) {
            try {
                listener.run();
            } catch (RuntimeException e) {
                log.warn("{} change listener failed", subject, e);
            }
        }
    }
}
