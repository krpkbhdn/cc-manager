package com.github.krpkbhdn.ccmanager.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;

import com.github.krpkbhdn.ccmanager.claude.session.SessionProperties;
import com.github.krpkbhdn.ccmanager.claude.session.SessionRegistry;
import com.github.krpkbhdn.ccmanager.claude.session.TranscriptService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class NotificationSoundServiceTest {

    private static final Duration INTERVAL = Duration.ofSeconds(1);

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override public ZoneOffset getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(java.time.ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }

    private final List<NotificationSound> played = new ArrayList<>();
    private final MutableClock clock = new MutableClock();
    private final NotificationService notifications = new NotificationService(new SessionRegistry(new SessionProperties(Duration.ofMinutes(10), Duration.ofMinutes(10)), mock(TranscriptService.class)));
    private NotificationSoundService service = create(played::add);

    private NotificationSoundService create(java.util.function.Consumer<NotificationSound> sink) {
        return new NotificationSoundService(notifications, (sound, volume) -> sink.accept(sound), new NotificationSoundProperties(true, 0.5, INTERVAL, Duration.ZERO), clock);
    }

    private void raise(NotificationKind kind) {
        service.onNotification(new AppNotification(1, clock.instant(), kind, 1, "s", "t", ""));
    }

    @Test
    void kindsMapToSounds() {
        assertThat(NotificationSound.forKind(NotificationKind.NEEDS_ACTION)).contains(NotificationSound.ATTENTION);
        assertThat(NotificationSound.forKind(NotificationKind.ERROR)).contains(NotificationSound.ATTENTION);
        assertThat(NotificationSound.forKind(NotificationKind.FINISHED)).contains(NotificationSound.SOFT);
        assertThat(NotificationSound.forKind(NotificationKind.INFO)).isEmpty();
    }

    @Test
    void infoStaysSilent() {
        raise(NotificationKind.INFO);
        assertThat(played).isEmpty();
    }

    @Test
    void burstIsCoalescedAndPlaysAgainAfterTheInterval() {
        raise(NotificationKind.FINISHED);
        raise(NotificationKind.FINISHED);
        assertThat(played).containsExactly(NotificationSound.SOFT);

        clock.advance(INTERVAL);
        raise(NotificationKind.FINISHED);
        assertThat(played).hasSize(2);
    }

    @Test
    void attentionWinsOverARecentSoftSoundButNotTheOtherWayRound() {
        raise(NotificationKind.FINISHED);
        raise(NotificationKind.NEEDS_ACTION);
        raise(NotificationKind.FINISHED);
        assertThat(played).containsExactly(NotificationSound.SOFT, NotificationSound.ATTENTION);
    }

    @Test
    void mutedPlaysNothingUntilEnabledAgain() {
        service.setEnabled(false);
        raise(NotificationKind.NEEDS_ACTION);
        assertThat(played).isEmpty();

        service.setEnabled(true);
        raise(NotificationKind.NEEDS_ACTION);
        assertThat(played).hasSize(1);
    }

    @Test
    void aFailingPlayerNeverPropagates() {
        service = create(sound -> { throw new IllegalStateException("no audio device"); });
        assertThatCode(() -> raise(NotificationKind.NEEDS_ACTION)).doesNotThrowAnyException();
    }
}
