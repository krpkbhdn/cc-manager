package com.github.krpkbhdn.ccmanager.author;

/** One versioned scope of the profile (user or project): what to tell the user, Claude's instructions and the deterministic pre-install. */
public record ScopeSetup(int version, String title, String summary, String prompt, ExtensionSet extensions) {

    public ScopeSetup {
        extensions = extensions == null ? new ExtensionSet(null, null, null, null) : extensions;
    }
}
