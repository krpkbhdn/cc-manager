package com.github.krpkbhdn.ccmanager.notification;

import java.time.Instant;

public record AppNotification(long id, Instant at, NotificationKind kind, long projectId, String ccmSessionId, String title, String body) {}
