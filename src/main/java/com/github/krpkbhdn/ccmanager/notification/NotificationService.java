package com.github.krpkbhdn.ccmanager.notification;

import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

/** Turns session status transitions (WORKING to IDLE, NEEDS_ACTION, errors) into notifications. */
@Service
public class NotificationService {

    /** Newest last, in-memory, capped. */
    public List<AppNotification> history() { throw new UnsupportedOperationException("TODO"); }

    /** @return unsubscribe handle; callback runs on an arbitrary thread */
    public Runnable subscribe(Consumer<AppNotification> listener) { throw new UnsupportedOperationException("TODO"); }
}
