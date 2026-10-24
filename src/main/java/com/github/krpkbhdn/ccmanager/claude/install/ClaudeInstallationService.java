package com.github.krpkbhdn.ccmanager.claude.install;

import org.springframework.stereotype.Service;

/** Finds the native claude binary (PATH, then the native-installer location) and reads version and login state. */
@Service
public class ClaudeInstallationService {

    /** Cached result of the last detection (detected eagerly at startup). */
    public ClaudeInstallation current() { throw todo(); }

    /** Runs detection again (UI check-again button). */
    public ClaudeInstallation recheck() { throw todo(); }

    private static UnsupportedOperationException todo() { return new UnsupportedOperationException("TODO"); }
}
