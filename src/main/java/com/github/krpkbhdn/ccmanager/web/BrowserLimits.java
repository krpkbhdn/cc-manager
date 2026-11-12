package com.github.krpkbhdn.ccmanager.web;

import java.time.Duration;

/** How many embedded browsers may stay alive at once and how long an unused one is kept. */
public record BrowserLimits(int maxLiveBrowsers, Duration idleBrowserTtl) {
}
