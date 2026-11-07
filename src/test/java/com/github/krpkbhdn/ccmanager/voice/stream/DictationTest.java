package com.github.krpkbhdn.ccmanager.voice.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.github.krpkbhdn.ccmanager.voice.VoiceProperties;
import com.github.krpkbhdn.ccmanager.voice.recognize.Recognizer;
import com.github.krpkbhdn.ccmanager.voice.recognize.Transcript;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Queue;
import java.util.concurrent.Executor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DictationTest {

    private static final int BLOCK = 1600;
    private static final int TICK_BLOCKS = 15;
    private static final int PAUSE_BLOCKS = 7;
    private static final int WINDOW_BLOCKS = 100;

    private final Recognizer recognizer = mock(Recognizer.class);
    private final DictationListener listener = mock(DictationListener.class);
    private final VoiceProperties properties = new VoiceProperties("auto", Duration.ofSeconds(10),
            Duration.ofMillis(1500), Duration.ofMillis(700), 0.01, 0.5, 4,
            Duration.ofSeconds(30), Duration.ofMinutes(5), Duration.ofMinutes(2), Duration.ofSeconds(60));

    @BeforeEach
    void setUp() throws IOException {
        when(recognizer.transcribe(any(), anyString())).thenReturn(new Transcript("hello", "en"));
    }

    private Dictation dictation(Executor executor) {
        return new Dictation(recognizer, new SpeechGate(properties.speechRms()), properties, "en", listener, executor);
    }

    private static void feed(Dictation dictation, int blocks, float level) {
        var block = new float[BLOCK];
        Arrays.fill(block, level);
        for (int i = 0; i < blocks; i++) {
            dictation.feed(block);
        }
    }

    @Test
    void emitsPartialOnTick() {
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS - 1, 0.1f);
        verifyNoResults();
        feed(dictation, 1, 0.1f);
        verify(listener).onPartial("hello");
        verify(listener, never()).onFinal(anyString());
    }

    @Test
    void emitsFinalOnPauseAndResetsWindow() throws IOException {
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS - PAUSE_BLOCKS, 0.1f);
        feed(dictation, PAUSE_BLOCKS, 0f);
        verify(listener).onFinal("hello");
        feed(dictation, TICK_BLOCKS - 1, 0f);
        verify(recognizer, times(1)).transcribe(any(), anyString());
    }

    @Test
    void emitsFinalWhenWindowFills() {
        var dictation = dictation(Runnable::run);
        feed(dictation, WINDOW_BLOCKS + TICK_BLOCKS, 0.1f);
        verify(listener).onFinal("hello");
    }

    @Test
    void skipsTickWhileRecognitionIsBusy() throws IOException {
        Queue<Runnable> pending = new ArrayDeque<>();
        var dictation = dictation(pending::add);
        feed(dictation, TICK_BLOCKS * 2, 0.1f);
        assertThat(pending).hasSize(1);
        pending.remove().run();
        verify(recognizer, times(1)).transcribe(any(), anyString());
        feed(dictation, TICK_BLOCKS, 0.1f);
        assertThat(pending).hasSize(1);
    }

    @Test
    void ignoresEmptyText() throws IOException {
        when(recognizer.transcribe(any(), anyString())).thenReturn(new Transcript("  ", "en"));
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS, 0.1f);
        dictation.stop();
        verifyNoResults();
    }

    @Test
    void flushesFinalOnStopAndIgnoresLaterAudio() throws IOException {
        var dictation = dictation(Runnable::run);
        feed(dictation, 5, 0.1f);
        dictation.stop();
        verify(listener).onFinal("hello");
        feed(dictation, TICK_BLOCKS, 0.1f);
        verify(recognizer, times(1)).transcribe(any(), anyString());
    }

    @Test
    void stopWithoutAnyAudioNeverCallsRecognizerOrListener() {
        var dictation = dictation(Runnable::run);
        dictation.stop();
        dictation.stop();
        verifyNoInteractions(recognizer);
        verifyNoResults();
    }

    @Test
    void repeatedStopFlushesOnlyOnce() throws IOException {
        var dictation = dictation(Runnable::run);
        feed(dictation, 5, 0.1f);
        dictation.stop();
        dictation.stop();
        verify(recognizer, times(1)).transcribe(any(), anyString());
    }

    @Test
    void emptyBlockIsHarmless() {
        var dictation = dictation(Runnable::run);
        dictation.feed(new float[0]);
        verifyNoInteractions(recognizer);
        verifyNoResults();
    }

    @Test
    void reportsRecognitionFailureAndKeepsGoing() throws IOException {
        var failure = new IOException("boom");
        when(recognizer.transcribe(any(), anyString())).thenThrow(failure).thenReturn(new Transcript("again", "en"));
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS, 0.1f);
        verify(listener).onError(failure);
        feed(dictation, TICK_BLOCKS, 0.1f);
        verify(listener).onPartial("again");
    }

    @Test
    void errorInRecognitionIsReportedAndDoesNotLeaveRecognizerBusy() throws IOException {
        var failure = new LinkageError("native");
        when(recognizer.transcribe(any(), anyString())).thenThrow(failure).thenReturn(new Transcript("again", "en"));
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS, 0.1f);
        verify(listener).onError(failure);
        feed(dictation, TICK_BLOCKS, 0.1f);
        verify(listener).onPartial("again");
    }

    @Test
    void stopFlushDoesNotRepeatFinalOfTickInFlight() throws IOException {
        Queue<Runnable> pending = new ArrayDeque<>();
        var dictation = dictation(pending::add);
        feed(dictation, TICK_BLOCKS - PAUSE_BLOCKS, 0.1f);
        feed(dictation, PAUSE_BLOCKS, 0f);
        dictation.stop();
        assertThat(pending).hasSize(2);
        pending.remove().run();
        pending.remove().run();
        verify(listener, times(1)).onFinal("hello");
        verify(recognizer, times(1)).transcribe(any(), anyString());
    }

    @Test
    void windowStaysBoundedWhileRecognitionKeepsFailing() throws IOException {
        var longest = new int[1];
        when(recognizer.transcribe(any(), anyString())).thenAnswer(call -> {
            longest[0] = Math.max(longest[0], call.<float[]>getArgument(0).length);
            throw new IOException("boom");
        });
        var dictation = dictation(Runnable::run);
        feed(dictation, WINDOW_BLOCKS * 3, 0.1f);
        assertThat(longest[0]).isEqualTo(WINDOW_BLOCKS * BLOCK);
    }

    @Test
    void reportsErrorOnlyOncePerFailureSeries() throws IOException {
        when(recognizer.transcribe(any(), anyString())).thenThrow(new IOException("boom"));
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS * 4, 0.1f);
        verify(listener, times(1)).onError(any());
    }

    @Test
    void reportsErrorAgainAfterSuccess() throws IOException {
        var boom = new IOException("boom");
        when(recognizer.transcribe(any(), anyString())).thenThrow(boom).thenReturn(new Transcript("ok", "en"))
                .thenThrow(boom);
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS * 3, 0.1f);
        verify(listener, times(2)).onError(boom);
    }

    @Test
    void doesNotRepeatUnchangedPartial() {
        var dictation = dictation(Runnable::run);
        feed(dictation, TICK_BLOCKS * 2, 0.1f);
        verify(listener, times(1)).onPartial("hello");
    }

    private void verifyNoResults() {
        verify(listener, never()).onPartial(anyString());
        verify(listener, never()).onFinal(anyString());
        verify(listener, never()).onError(any());
    }

    @Test
    void reportsTheBlockLevelNormalisedToOne() {
        assertThat(Dictation.levelOf(new float[0])).isZero();
        assertThat(Dictation.levelOf(new float[] {0f, 0f})).isZero();
        assertThat(Dictation.levelOf(new float[] {0.001f, -0.001f})).isZero();
        assertThat(Dictation.levelOf(new float[] {0.05f, -0.05f})).isEqualTo(1.0);
        assertThat(Dictation.levelOf(new float[] {0.02f, -0.02f})).isBetween(0.72, 0.78);
        assertThat(Dictation.levelOf(new float[] {0.5f, -0.5f})).isEqualTo(1.0);
    }

    @Test
    void feedsTheLevelToTheListener() {
        var dictation = dictation(Runnable::run);
        feed(dictation, 1, 0.1f);
        verify(listener).onLevel(org.mockito.ArgumentMatchers.doubleThat(level -> level == 1.0));
    }
}
