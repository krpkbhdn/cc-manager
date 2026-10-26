package com.github.krpkbhdn.ccmanager.ui

import org.assertj.core.api.Assertions.assertThat

private const val AWAIT_MILLIS = 5_000L
private const val POLL_MILLIS = 20L

/** Polls [condition] until it holds, for state that changes on a background scope. */
fun await(condition: () -> Boolean) {
    val deadline = System.currentTimeMillis() + AWAIT_MILLIS
    while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(POLL_MILLIS)
    assertThat(condition()).describedAs("condition within ${AWAIT_MILLIS}ms").isTrue()
}
