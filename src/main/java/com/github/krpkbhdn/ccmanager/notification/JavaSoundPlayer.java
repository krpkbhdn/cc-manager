package com.github.krpkbhdn.ccmanager.notification;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Plays the bundled WAVs through javax.sound on a daemon thread; the first failure (e.g. no audio device) is logged at warn, later ones at debug. */
@Component
class JavaSoundPlayer implements SoundPlayer {

    private final NotificationSoundProperties properties;

    JavaSoundPlayer(NotificationSoundProperties properties) {
        this.properties = properties;
    }

    private static final Logger log = LoggerFactory.getLogger(JavaSoundPlayer.class);
    private static final double DECIBELS_PER_DECADE = 20.0;
    private static final long MICROS_PER_MILLI = 1000;

    private final AtomicBoolean warned = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(task -> {
        var thread = new Thread(task, "notification-sound");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void play(NotificationSound sound, double volume) {
        try {
            executor.execute(() -> playNow(sound, volume));
        } catch (RuntimeException e) {
            log.debug("Notification sound not scheduled", e);
        }
    }

    private void playNow(NotificationSound sound, double volume) {
        try (InputStream raw = JavaSoundPlayer.class.getResourceAsStream(sound.resource())) {
            if (raw == null) {
                reportFailure(new IOException("Missing sound resource " + sound.resource()));
                return;
            }
            try (var clip = AudioSystem.getClip()) {
                clip.open(AudioSystem.getAudioInputStream(new BufferedInputStream(raw)));
                applyVolume(clip, volume);
                clip.start();
                // STOP fires when data is handed to the device, not when it is audible: keep the line open past the end.
                Thread.sleep(clip.getMicrosecondLength() / MICROS_PER_MILLI + properties.lineTail().toMillis());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (IOException | javax.sound.sampled.LineUnavailableException | javax.sound.sampled.UnsupportedAudioFileException | RuntimeException e) {
            reportFailure(e);
        }
    }

    private void reportFailure(Exception e) {
        if (warned.compareAndSet(false, true)) {
            log.warn("Notification sound unavailable (further failures are logged at debug)", e);
        } else {
            log.debug("Notification sound unavailable", e);
        }
    }

    private static void applyVolume(Clip clip, double volume) {
        if (volume < 1.0 && clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            var gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            var decibels = (float) (DECIBELS_PER_DECADE * Math.log10(Math.max(volume, Double.MIN_NORMAL)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
        }
    }
}
