package com.github.krpkbhdn.ccmanager.review;

import static com.github.krpkbhdn.ccmanager.config.PropertyRules.*;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Tunables of visual reviews: how long reviews are kept (all of them by creation time, whatever their status), how often cleanup runs,
 * and limits per submission and per project. The pixel limits also bound the memory the annotator needs to decode an image.
 */
@ConfigurationProperties("cc-manager.review")
public record ReviewProperties(@DefaultValue("7d") Duration retention, @DefaultValue("1h") Duration cleanupInterval, @DefaultValue("20") int maxImages,
        @DefaultValue("20971520") long maxImageBytes, @DefaultValue("8192") int maxImageSide, @DefaultValue("36000000") long maxImagePixels,
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
