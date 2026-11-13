package com.github.krpkbhdn.ccmanager.workspace;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Detects which of the {@link KnownIdes} are installed; the result is cached until {@link #recheck()}. */
@Service
public class IdeDetector {

    private final HostEnvironment host;
    private final IdeLocator locator;
    private volatile List<InstalledIde> current;

    @Autowired
    public IdeDetector(HostEnvironment host, IdeLocator locator) {
        this.host = host;
        this.locator = locator;
    }

    /** Cached result of the last detection (detected on first use). */
    public List<InstalledIde> detect() {
        var cached = current;
        return cached != null ? cached : recheck();
    }

    public synchronized List<InstalledIde> recheck() {
        current = KnownIdes.ALL.stream()
                .flatMap(ide -> locator.find(host, ide).map(command -> new InstalledIde(ide.id(), ide.name(), command)).stream())
                .toList();
        return current;
    }
}
