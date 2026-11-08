package com.github.krpkbhdn.ccmanager.gitlab;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Settings of the GitLab CLI calls. */
@ConfigurationProperties("cc-manager.gitlab")
public record GlabProperties(
        @DefaultValue("glab") String executable,
        @DefaultValue("60s") Duration timeout,
        @DefaultValue("10m") Duration readyTtl) {

    public GlabProperties {
        positive("cc-manager.gitlab.timeout", timeout);
        positive("cc-manager.gitlab.ready-ttl", readyTtl);
    }
}
