package com.github.krpkbhdn.ccmanager.ui.web.browser

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.cef.CefApp
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class JcefRuntimeTest {
    @Test
    fun reports_a_missing_browser_library(@TempDir home: Path) {
        val result = JcefRuntime.availability(home, "windows 11", cefPresent = false)

        assertThat(result).isInstanceOf(Availability.Unsupported::class.java)
    }

    @Test
    fun reports_a_missing_helper_executable(@TempDir home: Path) {
        val result = JcefRuntime.availability(home, "windows 11", cefPresent = true)

        assertThat((result as Availability.Unsupported).reason).contains("jcef_helper")
    }

    @Test
    fun is_ready_when_the_windows_helper_exists(@TempDir home: Path) {
        Files.createDirectories(home.resolve("bin"))
        Files.createFile(home.resolve("bin/jcef_helper.exe"))

        assertThat(JcefRuntime.availability(home, "windows 11", cefPresent = true)).isEqualTo(Availability.Ready)
    }

    @Test
    fun finds_the_linux_helper_in_lib(@TempDir home: Path) {
        Files.createDirectories(home.resolve("lib"))
        val helper = Files.createFile(home.resolve("lib/jcef_helper"))

        assertThat(JcefRuntime.helperExecutable(home, "linux")).isEqualTo(helper)
    }

    @Test
    fun does_not_look_for_the_windows_helper_on_linux(@TempDir home: Path) {
        Files.createDirectories(home.resolve("bin"))
        Files.createFile(home.resolve("bin/jcef_helper.exe"))

        assertThat(JcefRuntime.helperExecutable(home, "linux")).isNull()
    }

    @Test
    fun a_terminated_start_fails_at_once() {
        val started = System.nanoTime()

        assertThatThrownBy { JcefRuntime.awaitInitialized({ CefApp.CefAppState.TERMINATED }, 30_000) }
            .isInstanceOf(IllegalStateException::class.java)
        assertThat(System.nanoTime() - started).isLessThan(5_000_000_000L)
    }

    @Test
    fun a_start_that_never_finishes_times_out() {
        assertThatThrownBy { JcefRuntime.awaitInitialized({ CefApp.CefAppState.INITIALIZING }, 100, 10) }
            .hasMessageContaining("did not initialize")
    }

    @Test
    fun waits_until_the_state_is_initialized() {
        val states = ArrayDeque(listOf(CefApp.CefAppState.NEW, CefApp.CefAppState.INITIALIZING, CefApp.CefAppState.INITIALIZED))

        JcefRuntime.awaitInitialized({ states.removeFirst() }, 5_000, 1)

        assertThat(states).isEmpty()
    }

    @Test
    fun shutdown_is_safe_when_never_started() {
        JcefRuntime.shutdown()
    }
}
