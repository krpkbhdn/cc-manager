package com.github.krpkbhdn.ccmanager.web;

import com.github.krpkbhdn.ccmanager.review.ReviewStatus;
import java.time.Instant;

/** A numbered batch of items sent to an agent together; {@code sentAt} and {@code settledAt} are null until then. */
public record WebRevision(long id, long projectId, int number, String ccmSessionId, String note, ReviewStatus status, Instant createdAt,
        Instant sentAt, Instant settledAt) {
}
