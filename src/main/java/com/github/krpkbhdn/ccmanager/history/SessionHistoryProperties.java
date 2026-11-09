package com.github.krpkbhdn.ccmanager.history;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of the session history index: how often transcripts are scanned, how many entries the index may hold, how
 * many hits a search returns, how many entries one eviction transaction deletes and how many bytes and lines one
 * indexing transaction takes (small, so the single SQLite writer lock is held briefly), and how many sensitive tool ids
 * a transcript remembers (to skip their results, even duplicated ones).
 */
@ConfigurationProperties("cc-manager.history")
public record SessionHistoryProperties(@DefaultValue("30s") Duration scanInterval, @DefaultValue("200000") int maxEntries, @DefaultValue("100") int searchLimit,
        @DefaultValue("5000") int evictChunk, @DefaultValue("1048576") long batchBytes, @DefaultValue("300") int batchLines,
        @DefaultValue("1000") int maxSensitiveToolIds) {

    public SessionHistoryProperties {
        positive("cc-manager.history.scan-interval", scanInterval);
        positive("cc-manager.history.max-entries", maxEntries);
        positive("cc-manager.history.search-limit", searchLimit);
        positive("cc-manager.history.evict-chunk", evictChunk);
        positive("cc-manager.history.batch-bytes", batchBytes);
        positive("cc-manager.history.batch-lines", batchLines);
        positive("cc-manager.history.max-sensitive-tool-ids", maxSensitiveToolIds);
    }
}
