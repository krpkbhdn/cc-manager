package com.github.krpkbhdn.ccmanager.usage.history;

/** Cost of one session within a period; claudeSessionId is null when Claude never reported it. */
public record SessionCost(String claudeSessionId, String ccmSessionId, long projectId, String model, double costUsd) {
}
