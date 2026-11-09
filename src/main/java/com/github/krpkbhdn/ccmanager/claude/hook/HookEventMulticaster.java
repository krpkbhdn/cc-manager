package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.PayloadApplicationEvent;
import org.springframework.context.event.SimpleApplicationEventMulticaster;
import org.springframework.context.support.AbstractApplicationContext;
import org.springframework.core.ResolvableType;
import org.springframework.stereotype.Component;

/**
 * Spring stops at the first listener that throws; for hook events and status line reports every listener must still see
 * the event (a failing one must not blind the session registry), so those are delivered listener by listener.
 */
@Component(AbstractApplicationContext.APPLICATION_EVENT_MULTICASTER_BEAN_NAME)
public class HookEventMulticaster extends SimpleApplicationEventMulticaster {

    private static final Logger log = LoggerFactory.getLogger(HookEventMulticaster.class);
    private static final long WARN_INTERVAL_NANOS = Duration.ofSeconds(30).toNanos();

    private final Map<String, AtomicLong> lastWarnNanosByListener = new ConcurrentHashMap<>();

    @Override
    public void multicastEvent(ApplicationEvent event, ResolvableType eventType) {
        if (!(event instanceof PayloadApplicationEvent<?> payloadEvent) || !isolated(payloadEvent.getPayload())) {
            super.multicastEvent(event, eventType);
            return;
        }
        var type = eventType != null ? eventType : ResolvableType.forInstance(event);
        for (var listener : getApplicationListeners(event, type)) {
            try {
                invokeListener(listener, event);
            } catch (RuntimeException e) {
                warn(listener.getClass().getName(), payloadEvent.getPayload(), e);
            }
        }
    }

    private static boolean isolated(Object payload) {
        return payload instanceof HookEvent || payload instanceof StatusLineReport;
    }

    private void warn(String listener, Object payload, RuntimeException failure) {
        var lastWarn = lastWarnNanosByListener.computeIfAbsent(listener, name -> new AtomicLong(System.nanoTime() - WARN_INTERVAL_NANOS));
        var now = System.nanoTime();
        var last = lastWarn.get();
        if (now - last >= WARN_INTERVAL_NANOS && lastWarn.compareAndSet(last, now)) {
            log.warn("Listener {} failed on {} (further failures are logged at debug)", listener, payload.getClass().getSimpleName(), failure);
        } else {
            log.debug("Listener {} failed on {}", listener, payload.getClass().getSimpleName(), failure);
        }
    }
}
