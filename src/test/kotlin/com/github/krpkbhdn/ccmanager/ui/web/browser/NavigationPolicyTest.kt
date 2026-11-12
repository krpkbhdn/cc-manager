package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class NavigationPolicyTest {
    @ParameterizedTest
    @ValueSource(strings = ["http://localhost:3000/", "HTTPS://example.com/a?b=1", "file:///C:/tmp/a.html", "about:blank", "  https://x.dev "])
    fun allows_web_and_local_pages(url: String) {
        assertThat(NavigationPolicy.isAllowed(url)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["javascript:alert(1)", "JaVaScRiPt:alert(1)", "data:text/html,<b>x</b>", "ftp://host/f", "chrome://gpu", "about:srcdoc", "blob:http://x/1", "java\tscript:alert(1)", "example.com", ""])
    fun rejects_everything_else(url: String) {
        assertThat(NavigationPolicy.isAllowed(url)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["about:srcdoc", "about:blank", "blob:http://x/1", "data:text/html,<b>x</b>", "https://x.dev/frame"])
    fun sub_frames_may_load_embedded_documents(url: String) {
        assertThat(NavigationPolicy.isAllowedFromPage(url, WEB, mainFrame = false)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["about:srcdoc", "blob:http://x/1", "data:text/html,<b>x</b>", "javascript:alert(1)"])
    fun the_top_level_stays_restricted(url: String) {
        assertThat(NavigationPolicy.isAllowedFromPage(url, WEB, mainFrame = true)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["javascript:alert(1)", "ftp://host/f", "chrome://gpu"])
    fun sub_frames_still_cannot_run_scripts_or_open_other_schemes(url: String) {
        assertThat(NavigationPolicy.isAllowedFromPage(url, WEB, mainFrame = false)).isFalse()
    }

    @Test
    fun a_web_page_cannot_reach_local_files() {
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/secret.txt", WEB)).isFalse()
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/secret.txt", WEB, mainFrame = false)).isFalse()
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/secret.txt", "about:blank")).isFalse()
    }

    @Test
    fun a_local_page_may_embed_local_files_and_open_web_pages_and_navigate_to_local_files() {
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/b.html", "file:///C:/a.html")).isTrue()
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/b.html", "file:///C:/a.html", mainFrame = false)).isTrue()
        assertThat(NavigationPolicy.isAllowedFromPage("https://x.dev/", "file:///C:/a.html")).isTrue()
    }

    @Test
    fun a_web_page_may_open_web_pages() {
        assertThat(NavigationPolicy.isAllowedFromPage("https://x.dev/", WEB)).isTrue()
        assertThat(NavigationPolicy.isAllowedFromPage("https://x.dev/", "https://y.dev/")).isTrue()
    }

    @Test
    fun scheme_of_reports_the_scheme_only() {
        assertThat(NavigationPolicy.schemeOf(" JavaScript:alert(1)")).isEqualTo("javascript")
        assertThat(NavigationPolicy.schemeOf("no scheme")).isNull()
    }

    @Test
    fun require_throws_for_a_blocked_url() {
        assertThatThrownBy { NavigationPolicy.require("javascript:alert(1)") }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun require_returns_the_trimmed_url() {
        assertThat(NavigationPolicy.require(" http://a.b/ ")).isEqualTo("http://a.b/")
    }

    private companion object {
        const val WEB = "https://example.com/"
    }
}
