package com.github.krpkbhdn.ccmanager.notification;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Notification feed tunables: how many notifications the in-memory history keeps and the window in which an identical one counts as a duplicate. */
@ConfigurationProperties("cc-manager.notification")
public record NotificationProperties(@DefaultValue("200") int historyLimit, @DefaultValue("2s") Duration duplicateWindow) {

    public NotificationProperties {
        positive("cc-manager.notification.history-limit", historyLimit);
        nonNegative("cc-manager.notification.duplicate-window", duplicateWindow);
    }
}
