package com.github.krpkbhdn.ccmanager.update;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Self-update tunables; an empty version (a run from sources) turns the update check off. */
@ConfigurationProperties("cc-manager.update")
public record UpdateProperties(
        @DefaultValue("") String version,
        @DefaultValue("krpkbhdn/cc-manager") String repository,
        @DefaultValue("https://api.github.com") String apiBaseUrl,
        @DefaultValue("6") int checkIntervalHours,
        @DefaultValue("15") int requestTimeoutSeconds) {

    public boolean enabled() {
        return !version.isBlank();
    }
}
