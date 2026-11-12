package com.github.krpkbhdn.ccmanager.review;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of visual reviews: how long reviews are kept (all of them by creation time, whatever their status), how often cleanup runs,
 * and limits per submission and per project. The pixel limits also bound the memory the annotator needs to decode an image (4 bytes per pixel, 256 MB at the default),
 * and are sized so a 2560 px wide page up to about 12000 css px tall at device scale 1.25 is stored untouched.
 */
@ConfigurationProperties("cc-manager.review")
public record ReviewProperties(@DefaultValue("7d") Duration retention, @DefaultValue("1h") Duration cleanupInterval, @DefaultValue("20") int maxImages,
        @DefaultValue("52428800") long maxImageBytes, @DefaultValue("32767") int maxImageSide, @DefaultValue("64000000") long maxImagePixels,
        @DefaultValue("50") int maxReviewsPerProject) {

    public ReviewProperties {
        positive("cc-manager.review.retention", retention);
        positive("cc-manager.review.cleanup-interval", cleanupInterval);
        positive("cc-manager.review.max-images", maxImages);
        positive("cc-manager.review.max-image-bytes", maxImageBytes);
        positive("cc-manager.review.max-image-side", maxImageSide);
        positive("cc-manager.review.max-image-pixels", maxImagePixels);
        positive("cc-manager.review.max-reviews-per-project", maxReviewsPerProject);
    }
}
