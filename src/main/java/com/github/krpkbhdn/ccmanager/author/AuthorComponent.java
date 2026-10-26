package com.github.krpkbhdn.ccmanager.author;

/** A separately versioned part of the author's setup; project setup is decided per project, the rest once. */
public enum AuthorComponent {
    USER_SETUP, PROJECT_SETUP, TEAMS;

    public boolean perProject() {
        return this == PROJECT_SETUP;
    }
}
