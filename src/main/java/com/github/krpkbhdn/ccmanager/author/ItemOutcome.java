package com.github.krpkbhdn.ccmanager.author;

import com.github.krpkbhdn.ccmanager.author.install.InstallOutcome;

public record ItemOutcome(String itemId, InstallOutcome outcome) {

    public boolean failed() {
        return outcome.hasFailures();
    }
}
