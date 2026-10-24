package com.github.krpkbhdn.ccmanager;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class CcManagerApplicationTest {

    @Test
    void contextStartsWithoutWebServer() {
        try (var context = CcManagerApplication.start(new String[0])) {
            assertThat(context.isRunning()).isTrue();
        }
    }
}
