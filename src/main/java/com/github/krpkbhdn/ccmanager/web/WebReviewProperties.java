package com.github.krpkbhdn.ccmanager.web;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.positive;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.ConstructorBinding;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of the web review: how much page markup reaches the feedback, screenshot and revision limits, how many browsers stay alive
 * and how long an idle one is kept, and how long approved revisions are kept (PENDING and SENT ones are never pruned).
 */
@ConfigurationProperties("cc-manager.web-review")
public record WebReviewProperties(@DefaultValue("600") int outerHtmlLimit,
        @DefaultValue({"display", "position", "width", "height", "margin-top", "margin-right", "margin-bottom", "margin-left",
                "padding-top", "padding-right", "padding-bottom", "padding-left",
                "border-top-width", "border-right-width", "border-bottom-width", "border-left-width",
                "color", "background-color", "font-family", "font-size", "font-weight", "line-height", "opacity", "z-index", "overflow",
                "flex-direction", "gap"}) List<String> computedKeys,
        @DefaultValue("5242880") long maxScreenshotBytes, @DefaultValue("200") int maxItemsPerRevision,
        @DefaultValue("30d") Duration retention, @DefaultValue("3") int maxLiveBrowsers, @DefaultValue("10m") Duration idleBrowserTtl) {

    private static final Duration DEFAULT_RETENTION = Duration.ofDays(30);
    private static final int DEFAULT_MAX_LIVE_BROWSERS = 3;
    private static final Duration DEFAULT_IDLE_BROWSER_TTL = Duration.ofMinutes(10);

    @ConstructorBinding
    public WebReviewProperties {
        positive("cc-manager.web-review.outer-html-limit", outerHtmlLimit);
        positive("cc-manager.web-review.max-screenshot-bytes", maxScreenshotBytes);
        positive("cc-manager.web-review.max-items-per-revision", maxItemsPerRevision);
        positive("cc-manager.web-review.retention", retention);
        positive("cc-manager.web-review.max-live-browsers", maxLiveBrowsers);
        positive("cc-manager.web-review.idle-browser-ttl", idleBrowserTtl);
        computedKeys = List.copyOf(computedKeys);
    }

    /** The limits with the default retention and browser settings. */
    public WebReviewProperties(int outerHtmlLimit, List<String> computedKeys, long maxScreenshotBytes, int maxItemsPerRevision) {
        this(outerHtmlLimit, computedKeys, maxScreenshotBytes, maxItemsPerRevision, DEFAULT_RETENTION, DEFAULT_MAX_LIVE_BROWSERS,
                DEFAULT_IDLE_BROWSER_TTL);
    }
}
