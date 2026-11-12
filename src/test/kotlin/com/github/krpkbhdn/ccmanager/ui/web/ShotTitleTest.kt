package com.github.krpkbhdn.ccmanager.ui.web

import com.github.krpkbhdn.ccmanager.ui.web.browser.PageShot
import java.time.LocalDateTime
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ShotTitleTest {
    private val now = LocalDateTime.of(2026, 11, 12, 9, 5)

    private fun shot(scale: Double = 1.0, cut: Boolean = false, cssHeight: Double = 800.0) = PageShot(ByteArray(0), 1, 1, 1000.0, cssHeight, scale, cut)

    @Test
    fun `the title is page title, device and time on one line`() {
        assertThat(ShotTitle.title("Settings\n  page", "http://a/", "iPhone 15", now)).isEqualTo("Settings page - iPhone 15 - 2026-11-12 09:05")
    }

    @Test
    fun `a blank page title falls back to host and path`() {
        assertThat(ShotTitle.title("  ", "http://localhost:3000/billing?x=1#top", "Responsive", now)).isEqualTo("localhost/billing - Responsive - 2026-11-12 09:05")
        assertThat(ShotTitle.title("", "http://localhost:3000/", "Responsive", now)).startsWith("localhost - Responsive")
    }

    @Test
    fun `a long page title is cut so the whole title stays within 120 characters and keeps its suffix`() {
        val title = ShotTitle.title("x".repeat(500), "http://a/", "Responsive", now)

        assertThat(title).hasSize(120).endsWith(" - Responsive - 2026-11-12 09:05")
    }

    @Test
    fun `the note names the address without query and fragment and the device size`() {
        assertThat(ShotTitle.note("http://a/p?token=1#x", "Responsive", 1280, 720, shot()))
            .isEqualTo("Captured by the user in the Web tab from http://a/p, Responsive 1280x720 CSS px")
    }

    @Test
    fun `the note tells a scaled or cut capture`() {
        assertThat(ShotTitle.note("http://a/", "Responsive", 1, 1, shot(scale = 0.62))).endsWith("; scaled to 62%")
        assertThat(ShotTitle.note("http://a/", "Responsive", 1, 1, shot(scale = 0.5, cut = true, cssHeight = 16000.0))).endsWith("; scaled to 50%; cut at 16000 px")
    }

    @Test
    fun `a long address is cut and the scale and cut remarks survive within the note cap`() {
        val note = ShotTitle.note("http://a/" + "p".repeat(3000), "Responsive", 1280, 720, shot(scale = 0.5, cut = true, cssHeight = 16000.0))

        assertThat(note).hasSize(1000).endsWith("Responsive 1280x720 CSS px; scaled to 50%; cut at 16000 px")
    }

    @Test
    fun `control characters in an address or title never reach the text`() {
        assertThat(ShotTitle.title("a‮bc", "http://a/", "R", now)).doesNotContain("‮", "")
    }
}
