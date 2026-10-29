package com.github.krpkbhdn.ccmanager.voice.asset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.github.krpkbhdn.ccmanager.config.AppProperties;
import com.github.krpkbhdn.ccmanager.download.VerifiedDownload;
import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import com.github.krpkbhdn.ccmanager.voice.device.ComputeDevice;
import com.github.krpkbhdn.ccmanager.voice.recognize.FfmRecognizer;
import com.github.krpkbhdn.ccmanager.voice.recognize.NativeRuntime;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Opt-in (CCM_REAL_WHISPER=1) in-process CPU run with the CPU runtime, base model and Silero VAD (about 70 MB).
 * The data directory outlives the JVM and is reused between runs: Windows keeps the loaded DLLs locked, so it is never deleted.
 */
class RealWhisperTest {

    private static final URI JFK = URI.create("https://raw.githubusercontent.com/ggml-org/whisper.cpp/master/samples/jfk.wav");
    private static final int WAV_HEADER_BYTES = 44;
    private static final int SAMPLE_RATE = 16_000;
    private static final Path DATA_DIR = Path.of("build", "real-whisper").toAbsolutePath();
    private static final VoiceProperties PROPERTIES = new VoiceProperties("en", Duration.ofSeconds(10),
            Duration.ofSeconds(1), Duration.ofSeconds(1), 0.01, 0.5, 4, Duration.ofSeconds(30), Duration.ofMinutes(5));

    private static NativeRuntime runtime;
    private static Path model;
    private static Path vad;
    private static float[] jfk;

    @BeforeAll
    static void installOnce() throws Exception {
        assumeTrue("1".equals(System.getenv("CCM_REAL_WHISPER")), "set CCM_REAL_WHISPER=1 to run");
        var assets = new VoiceAssetService(new AppProperties(DATA_DIR), new VerifiedDownload(), PROPERTIES);
        var build = RuntimeBuild.forCurrentPlatform().stream()
                .filter(candidate -> candidate.flavor() == RuntimeBuild.Flavor.CPU).findFirst().orElseThrow();
        var libraries = build.libraryDirectory(assets.install(build.asset(), progress -> { }));
        model = assets.install(WhisperModel.BASE_Q5_1.asset(), progress -> { });
        vad = assets.install(VoiceAsset.SILERO_VAD, progress -> { });
        assertThat(build.isCompleteIn(libraries)).isTrue();
        runtime = NativeRuntime.load(libraries, build.libraries());
        jfk = jfkSamples();
    }

    @Test
    void recognisesJfkSample() {
        try (var recognizer = open()) {
            assertThat(recognizer.transcribe(jfk, "en").text().toLowerCase(Locale.ROOT)).contains("ask not");
        }
    }

    @Test
    void survivesCyclesAndMisuse() {
        for (int cycle = 1; cycle <= 3; cycle++) {
            try (var recognizer = open()) {
                assertThat(recognizer.transcribe(jfk, "en").text().toLowerCase(Locale.ROOT)).contains("ask not");
                assertThat(recognizer.transcribe(new float[10 * SAMPLE_RATE], "en").text()).isEmpty();
            }
        }

        var recognizer = open();
        try {
            assertThat(recognizer.transcribe(new float[0], "en").text()).isEmpty();
            assertThat(recognizer.transcribe(new float[SAMPLE_RATE / 20], "en").text()).isEmpty();
            assertThat(recognizer.transcribe(Arrays.copyOf(jfk, SAMPLE_RATE / 20), "en").text()).isEmpty();
            var detected = recognizer.transcribe(jfk, "auto");
            System.out.printf("RW auto lang=%s text='%s'%n", detected.language(), detected.text());
            assertThat(detected.text().toLowerCase(Locale.ROOT)).contains("ask not");
        } finally {
            recognizer.close();
        }
        assertThatCode(recognizer::close).doesNotThrowAnyException();
        assertThatThrownBy(() -> recognizer.transcribe(jfk, "en")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void openingRunsLayoutSelfCheckAndLanguageIsValidatedBeforeNativeCall() {
        assertThatCode(() -> open().close()).doesNotThrowAnyException();
        try (var recognizer = open()) {
            for (var invalid : new String[] {"ua", "xx"}) {
                assertThatThrownBy(() -> recognizer.transcribe(jfk, invalid)).isInstanceOf(IllegalArgumentException.class);
            }
            for (var valid : new String[] {"uk", "auto"}) {
                assertThat(recognizer.transcribe(jfk, valid)).isNotNull();
            }
        }
    }

    private static FfmRecognizer open() {
        return FfmRecognizer.open(runtime, model, vad, ComputeDevice.CPU, PROPERTIES);
    }

    private static float[] jfkSamples() throws Exception {
        var response = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL).build()
                .send(HttpRequest.newBuilder(JFK).build(), HttpResponse.BodyHandlers.ofByteArray());
        var pcm = ByteBuffer.wrap(response.body(), WAV_HEADER_BYTES, response.body().length - WAV_HEADER_BYTES)
                .order(ByteOrder.LITTLE_ENDIAN);
        var samples = new float[pcm.remaining() / 2];
        for (int i = 0; i < samples.length; i++) {
            samples[i] = pcm.getShort() / (float) Short.MAX_VALUE;
        }
        return samples;
    }
}
