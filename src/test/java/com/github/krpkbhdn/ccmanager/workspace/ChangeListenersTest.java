package com.github.krpkbhdn.ccmanager.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ChangeListenersTest {

    private final ChangeListeners listeners = new ChangeListeners("Test");

    @Test
    void firesEveryRegisteredListener() {
        var calls = new ArrayList<String>();
        listeners.add(() -> calls.add("a"));
        listeners.add(() -> calls.add("b"));

        listeners.fire();

        assertThat(calls).containsExactly("a", "b");
    }

    @Test
    void throwingListenerDoesNotStopTheOthers() {
        List<String> calls = new ArrayList<>();
        listeners.add(() -> {
            throw new IllegalStateException("boom");
        });
        listeners.add(() -> calls.add("after"));

        listeners.fire();

        assertThat(calls).containsExactly("after");
    }

    @Test
    void unsubscribeHandleRemovesTheListener() {
        var calls = new ArrayList<String>();
        var unsubscribe = listeners.add(() -> calls.add("x"));

        unsubscribe.run();
        listeners.fire();

        assertThat(calls).isEmpty();
    }
}
