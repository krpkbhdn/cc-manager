package com.github.krpkbhdn.ccmanager.history;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of the session history index: how often transcripts are scanned and how many entries the index may hold and how many hits a search returns. */
@ConfigurationProperties("cc-manager.history")
public record SessionHistoryProperties(@DefaultValue("30s") Duration scanInterval, @DefaultValue("200000") int maxEntries, @DefaultValue("100") int searchLimit) {

    public SessionHistoryProperties {
        positive("cc-manager.history.scan-interval", scanInterval);
        positive("cc-manager.history.max-entries", maxEntries);
        positive("cc-manager.history.search-limit", searchLimit);
    }
}
