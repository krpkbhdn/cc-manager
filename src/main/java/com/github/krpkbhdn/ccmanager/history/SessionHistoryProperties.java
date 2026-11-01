package com.github.krpkbhdn.ccmanager.history;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of the session history index: how often transcripts are scanned and how many entries the index may hold. */
@ConfigurationProperties("cc-manager.history")
public record SessionHistoryProperties(@DefaultValue("30s") Duration scanInterval, @DefaultValue("200000") int maxEntries) {}
