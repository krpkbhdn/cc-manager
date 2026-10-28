package com.github.krpkbhdn.ccmanager.app;

import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Holds the UI's {@link AppControl}; Spring beans cannot reach the Compose UI any other way. */
@Component
public class AppControlRegistry {

    private final AtomicReference<AppControl> current = new AtomicReference<>();

    /** @return the action that disconnects it again */
    public Runnable connect(AppControl control) {
        current.set(control);
        return () -> current.compareAndSet(control, null);
    }

    public AppControl require() {
        var control = current.get();
        if (control == null) {
            throw new IllegalStateException("UI not ready");
        }
        return control;
    }
}
