package com.github.krpkbhdn.ccmanager.config;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the app keeps its local state (SQLite file). */
@ConfigurationProperties("cc-manager")
public record AppProperties(Path dataDir) {}
