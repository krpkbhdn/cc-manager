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
    fun a_local_page_may_embed_local_files_and_navigate_to_local_files_but_not_the_internet() {
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/b.html", "file:///C:/a.html")).isTrue()
        assertThat(NavigationPolicy.isAllowedFromPage("file:///C:/b.html", "file:///C:/a.html", mainFrame = false)).isTrue()
        assertThat(NavigationPolicy.isAllowedFromPage("https://x.dev/", "file:///C:/a.html")).isFalse()
        assertThat(NavigationPolicy.isAllowedFromPage("http://localhost:3000/", "file:///C:/a.html")).isTrue()
    }

    @Test
    fun a_page_may_open_its_own_origin() {
        assertThat(NavigationPolicy.isAllowedFromPage("https://EXAMPLE.com/other?x=1", WEB)).isTrue()
        assertThat(NavigationPolicy.isAllowedFromPage("https://example.com:443/", WEB)).isTrue()
    }

    @ParameterizedTest
    @ValueSource(strings = ["https://x.dev/", "http://example.com/", "https://example.com:8443/", "https://sub.example.com/", "https://example.com.evil.dev/", "https://example.com@evil.dev/", "http://8.8.8.8/", "http://172.32.0.1/", "http://11.0.0.1/", "http://999.1.1.1/"])
    fun a_page_cannot_navigate_its_top_level_to_another_origin(url: String) {
        assertThat(NavigationPolicy.isAllowedFromPage(url, WEB)).isFalse()
    }

    @ParameterizedTest
    @ValueSource(strings = ["http://localhost:3000/", "http://127.0.0.1:8080/", "http://127.1.2.3/", "http://[::1]:5173/", "http://10.1.2.3/", "http://172.16.0.1/", "http://172.31.9.9/", "http://192.168.1.5/", "http://app.localhost/"])
    fun a_page_may_open_loopback_and_private_network_hosts(url: String) {
        assertThat(NavigationPolicy.isAllowedFromPage(url, WEB)).isTrue()
    }

    @Test
    fun origin_of_drops_path_query_and_credentials() {
        assertThat(NavigationPolicy.originOf("https://User:pw@Example.com:8443/a/b?token=1#f")).isEqualTo("https://example.com:8443")
        assertThat(NavigationPolicy.originOf("javascript:alert(1)")).isNull()
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
