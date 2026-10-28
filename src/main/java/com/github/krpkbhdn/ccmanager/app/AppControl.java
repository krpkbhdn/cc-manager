package com.github.krpkbhdn.ccmanager.app;

import com.github.krpkbhdn.ccmanager.claude.launch.LaunchedSession;

/** What the Assistant's MCP tools may do to the running UI; implemented by the UI and connected at start-up. */
public interface AppControl {

    void openProject(long projectId, String tab);

    void navigate(String route);

    void attachSession(LaunchedSession session);

    void queuePrompt(String ccmSessionId, String text);
}
