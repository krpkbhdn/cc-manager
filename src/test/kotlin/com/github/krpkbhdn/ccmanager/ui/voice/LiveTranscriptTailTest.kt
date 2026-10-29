package com.github.krpkbhdn.ccmanager.ui.voice

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class LiveTranscriptTailTest {
    @Test
    fun `empty text stays empty`() = assertThat(LiveTranscriptTail.of("  \n ")).isEmpty()

    @Test
    fun `short text is returned as is`() = assertThat(LiveTranscriptTail.of("hello world")).isEqualTo("hello world")

    @Test
    fun `text of exactly 128 chars is not cut`() {
        val text = "a".repeat(128)
        assertThat(LiveTranscriptTail.of(text)).isEqualTo(text)
    }

    @Test
    fun `text of 129 chars keeps the last 128 behind an ellipsis`() {
        val text = "x" + "a".repeat(128)
        assertThat(LiveTranscriptTail.of(text)).isEqualTo("..." + "a".repeat(128))
    }

    @Test
    fun `long cyrillic text keeps its last 128 chars`() {
        val text = "привіт ".repeat(40)
        val tail = LiveTranscriptTail.of(text)
        assertThat(tail).startsWith("...").hasSize(131)
        assertThat(tail.removePrefix("...")).isEqualTo(text.trim().takeLast(128))
    }

    @Test
    fun `a cut that lands on a low surrogate moves past it`() {
        val tail = LiveTranscriptTail.of("😀".repeat(100) + "b").removePrefix("...")
        assertThat(tail).isEqualTo("😀".repeat(63) + "b").hasSize(127)
    }

    @Test
    fun `a zero or negative limit keeps only the ellipsis and never throws`() {
        assertThat(LiveTranscriptTail.of("hello", 0)).isEqualTo("...")
        assertThat(LiveTranscriptTail.of("hello", -5)).isEqualTo("...")
        assertThat(LiveTranscriptTail.of("", 0)).isEmpty()
        assertThat(LiveTranscriptTail.of("a😀", 1)).isEqualTo("...")
    }

    @Test
    fun `non breaking and other unicode spaces collapse like spaces`() {
        assertThat(LiveTranscriptTail.of("a  b c　d")).isEqualTo("a b c d")
        assertThat(LiveTranscriptTail.of("   ")).isEmpty()
    }

    @Test
    fun `a one megabyte transcript is cut fast and keeps its end`() {
        val text = "слово ".repeat(180_000) + "кінець"
        val started = System.nanoTime()
        val tail = LiveTranscriptTail.of(text)
        assertThat((System.nanoTime() - started) / 1_000_000).isLessThan(1000)
        assertThat(tail).startsWith("...").endsWith("кінець").hasSize(131)
    }

    @Test
    fun `129 chars of which the last 128 start after a space are cut at 128`() {
        val tail = LiveTranscriptTail.of("ab " + "c".repeat(127))
        assertThat(tail).isEqualTo("..." + " " + "c".repeat(127))
    }

    @Test
    fun `whitespace only inputs and line breaks collapse`() {
        assertThat(LiveTranscriptTail.of("\t\r\n")).isEmpty()
        assertThat(LiveTranscriptTail.of("a\r\nb\tc")).isEqualTo("a b c")
    }

    @Test
    fun `repeated whitespace collapses before measuring`() {
        assertThat(LiveTranscriptTail.of("  one \n\t two   three ")).isEqualTo("one two three")
    }
}
