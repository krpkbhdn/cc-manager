package com.github.krpkbhdn.ccmanager.ui.queue

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PromptQueuesTest {

    private val queues = PromptQueues()

    @Test
    fun promptsKeepTheOrderTheyWereAddedIn() {
        queues.add("a", "first")
        queues.add("a", "second")
        assertThat(queues.of("a").map { it.text }).containsExactly("first", "second")
    }

    @Test
    fun sessionsHaveSeparateQueues() {
        queues.add("a", "one")
        assertThat(queues.of("b")).isEmpty()
    }

    @Test
    fun removeDropsOnlyThatPrompt() {
        queues.add("a", "first")
        queues.add("a", "second")
        queues.remove("a", queues.of("a").first().id)
        assertThat(queues.of("a").map { it.text }).containsExactly("second")
    }

    @Test
    fun clearEmptiesOnlyThatSession() {
        queues.add("a", "one")
        queues.add("b", "two")
        queues.clear("a")
        assertThat(queues.of("a")).isEmpty()
        assertThat(queues.of("b")).hasSize(1)
    }

    @Test
    fun retainSessionsDiscardsTheQueuesOfEndedSessions() {
        queues.add("a", "one")
        queues.add("b", "two")
        queues.retainSessions(setOf("b"))
        assertThat(queues.of("a")).isEmpty()
        assertThat(queues.of("b")).hasSize(1)
    }
}
