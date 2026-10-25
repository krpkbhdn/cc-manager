package com.github.krpkbhdn.ccmanager.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

/** Plays a short sound for each delivered notification; bursts are coalesced and a pending ATTENTION is never swallowed by a SOFT one. */
@Service
@EnableConfigurationProperties(NotificationSoundProperties.class)
public class NotificationSoundService {

    private static final Logger log = LoggerFactory.getLogger(NotificationSoundService.class);

    private final SoundPlayer player;
    private final NotificationSoundProperties properties;
    private final Clock clock;
    private volatile boolean enabled;
    private Instant lastPlayedAt = Instant.MIN;
    private NotificationSound lastPlayed;

    @Autowired
    public NotificationSoundService(NotificationService notifications, SoundPlayer player, NotificationSoundProperties properties) {
        this(notifications, player, properties, Clock.systemUTC());
    }

    NotificationSoundService(NotificationService notifications, SoundPlayer player, NotificationSoundProperties properties, Clock clock) {
        this.player = player;
        this.properties = properties;
        this.clock = clock;
        this.enabled = properties.enabled();
        notifications.subscribe(this::onNotification);
    }

    /** The user's mute switch; the configured flag only sets the initial value. */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean enabled() {
        return enabled;
    }

    void onNotification(AppNotification notification) {
        if (enabled) {
            NotificationSound.forKind(notification.kind()).ifPresent(this::playCoalesced);
        }
    }

    private synchronized void playCoalesced(NotificationSound sound) {
        var now = clock.instant();
        var recent = Duration.between(lastPlayedAt, now).compareTo(properties.minInterval()) < 0;
        if (recent && !(sound == NotificationSound.ATTENTION && lastPlayed == NotificationSound.SOFT)) {
            return;
        }
        lastPlayedAt = now;
        lastPlayed = sound;
        try {
            player.play(sound, properties.volume());
        } catch (RuntimeException e) {
            log.debug("Notification sound failed", e);
        }
    }
}
