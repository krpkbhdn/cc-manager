package com.github.krpkbhdn.ccmanager.app;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;

class AppControlRegistryTest {

    private final AppControlRegistry registry = new AppControlRegistry();

    @Test
    void requireFailsWhenNothingIsConnected() {
        assertThatThrownBy(registry::require).isInstanceOf(IllegalStateException.class).hasMessage("UI not ready");
    }

    @Test
    void requireReturnsTheConnectedControlUntilItDisconnects() {
        var control = mock(AppControl.class);

        var disconnect = registry.connect(control);
        assertThat(registry.require()).isSameAs(control);

        disconnect.run();
        assertThatThrownBy(registry::require).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void staleDisconnectKeepsTheNewerControl() {
        var first = registry.connect(mock(AppControl.class));
        var second = mock(AppControl.class);
        registry.connect(second);

        first.run();

        assertThat(registry.require()).isSameAs(second);
    }
}
