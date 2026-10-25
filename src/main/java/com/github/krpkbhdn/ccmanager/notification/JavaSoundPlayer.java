package com.github.krpkbhdn.ccmanager.notification;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.LineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Plays the bundled WAVs through javax.sound on a daemon thread; a missing audio device is only logged at debug. */
@Component
class JavaSoundPlayer implements SoundPlayer {

    private static final Logger log = LoggerFactory.getLogger(JavaSoundPlayer.class);
    private static final double DECIBELS_PER_DECADE = 20.0;

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
                log.debug("Missing sound resource {}", sound.resource());
                return;
            }
            var clip = AudioSystem.getClip();
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.open(AudioSystem.getAudioInputStream(new BufferedInputStream(raw)));
            applyVolume(clip, volume);
            clip.start();
        } catch (IOException | javax.sound.sampled.LineUnavailableException | javax.sound.sampled.UnsupportedAudioFileException | RuntimeException e) {
            log.debug("Notification sound unavailable", e);
        }
    }

    private static void applyVolume(Clip clip, double volume) {
        if (clip.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            var gain = (FloatControl) clip.getControl(FloatControl.Type.MASTER_GAIN);
            var decibels = (float) (DECIBELS_PER_DECADE * Math.log10(Math.max(volume, Double.MIN_NORMAL)));
            gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), decibels)));
        }
    }
}
