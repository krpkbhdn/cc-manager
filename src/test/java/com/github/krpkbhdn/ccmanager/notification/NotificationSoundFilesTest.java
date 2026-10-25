package com.github.krpkbhdn.ccmanager.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.BufferedInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import org.junit.jupiter.api.Test;

class NotificationSoundFilesTest {

    private static final double MIN_PEAK_DBFS = -3.0;
    private static final double MIN_LEAD_IN_SECONDS = 0.35;
    private static final double MIN_TRAIL_SECONDS = 0.25;

    @Test
    void everySoundIsLoudSixteenBitPcmWithSilenceAroundItForSleepingOutputDevices() throws Exception {
        for (var sound : NotificationSound.values()) {
            try (var raw = NotificationSoundFilesTest.class.getResourceAsStream(sound.resource());
                 var audio = AudioSystem.getAudioInputStream(new BufferedInputStream(raw))) {
                var format = audio.getFormat();
                assertThat(format.getEncoding()).as(sound.name()).isEqualTo(AudioFormat.Encoding.PCM_SIGNED);
                assertThat(format.getSampleSizeInBits()).as(sound.name()).isEqualTo(16);
                assertThat(format.getSampleRate()).as(sound.name()).isEqualTo(44100f);

                var pcm = audio.readAllBytes();
                var peak = 0;
                var firstAudible = -1;
                var lastAudible = -1;
                for (var i = 0; i + 1 < pcm.length; i += 2) {
                    var sample = Math.abs((short) ((pcm[i] & 0xFF) | (pcm[i + 1] << 8)));
                    peak = Math.max(peak, sample);
                    if (sample > 500) {
                        lastAudible = i / 2;
                    }
                    if (firstAudible < 0 && sample > 500) {
                        firstAudible = i / 2;
                    }
                }
                assertThat(20 * Math.log10(peak / 32768.0)).as(sound.name() + " peak dBFS").isGreaterThan(MIN_PEAK_DBFS);
                assertThat((double) firstAudible / format.getSampleRate()).as(sound.name() + " lead-in").isGreaterThanOrEqualTo(MIN_LEAD_IN_SECONDS);
                assertThat((double) (pcm.length / 2 - lastAudible) / format.getSampleRate()).as(sound.name() + " trailing silence").isGreaterThanOrEqualTo(MIN_TRAIL_SECONDS);
            }
        }
    }
}
