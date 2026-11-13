package com.github.krpkbhdn.ccmanager.claude.hook;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class CurlSupportTest {

    private static boolean supports(String versionOutput) {
        return new CurlSupport(executable -> versionOutput).expandsEnvironment("curl");
    }

    @Test
    void curlFromTheMinimumVersionOnExpandsTheEnvironment() {
        assertThat(supports("curl 8.3.0 (x86_64) libcurl/8.3.0")).isTrue();
        assertThat(supports("curl 8.16.0 (x86_64-w64-mingw32)")).isTrue();
        assertThat(supports("curl 9.0.1 (x)")).isTrue();
    }

    @Test
    void olderOrUnparsableOutputCountsAsUnsupported() {
        assertThat(supports("curl 8.2.1 (x)")).isFalse();
        assertThat(supports("curl 7.88.1 (x)")).isFalse();
        assertThat(supports("")).isFalse();
        assertThat(supports("command not found")).isFalse();
    }

    @Test
    void aCurlThatCouldNotBeRunIsAskedAgainWhileATooOldOneIsRemembered() {
        var calls = new AtomicInteger();
        var support = new CurlSupport(executable -> calls.incrementAndGet() == 1 ? null : "curl 8.16.0");

        assertThat(support.expandsEnvironment("curl")).isFalse();
        assertThat(support.expandsEnvironment("curl")).isTrue();
        assertThat(support.expandsEnvironment("curl")).isTrue();
        assertThat(calls).hasValue(2);

        var oldCalls = new AtomicInteger();
        var old = new CurlSupport(executable -> {
            oldCalls.incrementAndGet();
            return "curl 7.81.0";
        });
        old.expandsEnvironment("curl");
        old.expandsEnvironment("curl");
        assertThat(oldCalls).hasValue(1);
    }

    @Test
    void theVersionIsAskedOnlyOncePerExecutable() {
        var calls = new AtomicInteger();
        var support = new CurlSupport(executable -> {
            calls.incrementAndGet();
            return "curl 8.16.0";
        });

        support.expandsEnvironment("curl");
        support.expandsEnvironment("curl");

        assertThat(calls).hasValue(1);
    }
}
