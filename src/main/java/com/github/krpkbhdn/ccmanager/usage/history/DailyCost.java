package com.github.krpkbhdn.ccmanager.usage.history;

import java.time.LocalDate;

/** Cost of one local day for a project and model; an unknown model is the empty string. */
public record DailyCost(LocalDate day, long projectId, String model, double costUsd) {
}
