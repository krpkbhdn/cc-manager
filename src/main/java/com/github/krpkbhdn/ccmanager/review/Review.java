package com.github.krpkbhdn.ccmanager.review;

import java.time.Instant;

public record Review(long id, long projectId, String ccmSessionId, String title, String note, ReviewStatus status, Instant createdAt, Instant settledAt) {
}
