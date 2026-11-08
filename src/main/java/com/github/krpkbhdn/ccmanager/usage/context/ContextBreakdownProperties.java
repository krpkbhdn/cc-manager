package com.github.krpkbhdn.ccmanager.usage.context;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.positive;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** How long {@code claude /context} may run, how long a measured breakdown is reused and how long a failure is remembered. */
@ConfigurationProperties("cc-manager.context-breakdown")
public record ContextBreakdownProperties(@DefaultValue("30s") Duration timeout, @DefaultValue("60s") Duration ttl,
        @DefaultValue("10s") Duration failureTtl) {

    public ContextBreakdownProperties {
        positive("cc-manager.context-breakdown.timeout", timeout);
        positive("cc-manager.context-breakdown.ttl", ttl);
        positive("cc-manager.context-breakdown.failure-ttl", failureTtl);
    }
}
