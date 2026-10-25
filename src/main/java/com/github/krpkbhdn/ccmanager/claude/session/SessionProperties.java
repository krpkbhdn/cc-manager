package com.github.krpkbhdn.ccmanager.claude.session;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Tunables of session tracking: how long a running subagent may stay silent before it is shown as inactive. */
@ConfigurationProperties("cc-manager.sessions")
public record SessionProperties(@DefaultValue("10m") Duration subagentStaleAfter) {}
