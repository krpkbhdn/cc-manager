package com.github.krpkbhdn.ccmanager.claude.hook;

import com.github.krpkbhdn.ccmanager.claude.event.HookEvent;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/** What the hook server needs of whoever settles held hook calls (implemented by the proposal service). */
public interface DecisionHolder {

    /** @return the hook output once settled, empty when the terminal dialog stays in charge */
    CompletableFuture<Optional<String>> hold(HookEvent event);

    void releaseAll();
}
