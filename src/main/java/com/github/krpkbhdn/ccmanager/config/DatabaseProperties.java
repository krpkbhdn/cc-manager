package com.github.krpkbhdn.ccmanager.config;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of the SQLite connection pool: how long a writer waits for the write lock and how many connections may be open. */
@ConfigurationProperties("cc-manager.database")
public record DatabaseProperties(@DefaultValue("5s") Duration busyTimeout, @DefaultValue("4") int maxConnections) {

    public DatabaseProperties {
        positive("cc-manager.database.busy-timeout", busyTimeout);
        positive("cc-manager.database.max-connections", maxConnections);
    }
}
