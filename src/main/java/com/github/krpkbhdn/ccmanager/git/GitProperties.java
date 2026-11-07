package com.github.krpkbhdn.ccmanager.git;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;

/** Git tab tunables: command timeouts, change-watcher debounce and size, diff and untracked-file caps. */
@ConfigurationProperties("cc-manager.git")
public record GitProperties(
        @DefaultValue("60s") Duration commandTimeout,
        @DefaultValue("10m") Duration syncTimeout,
        @DefaultValue("300ms") Duration watchDebounce,
        @DefaultValue("2000") int maxWatchedDirectories,
        @DefaultValue("2000") int maxDiffLines,
        @DefaultValue("1048576") long maxUntrackedBytes) {

    /** The values of an empty configuration, for code that runs outside Spring. */
    public static GitProperties defaults() {
        return new Binder(new MapConfigurationPropertySource()).bindOrCreate("cc-manager.git", GitProperties.class);
    }

    public GitProperties {
        positive("cc-manager.git.command-timeout", commandTimeout);
        positive("cc-manager.git.sync-timeout", syncTimeout);
        nonNegative("cc-manager.git.watch-debounce", watchDebounce);
        positive("cc-manager.git.max-watched-directories", maxWatchedDirectories);
        positive("cc-manager.git.max-diff-lines", maxDiffLines);
        positive("cc-manager.git.max-untracked-bytes", maxUntrackedBytes);
    }
}
