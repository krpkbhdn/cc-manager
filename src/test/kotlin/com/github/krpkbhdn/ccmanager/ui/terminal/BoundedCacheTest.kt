package com.github.krpkbhdn.ccmanager.ui.terminal

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BoundedCacheTest {
    @Test
    fun dropsLeastRecentlyUsedEntryBeyondLimit() {
        val cache = BoundedCache<Int, String>(2)
        cache.getOrPut(1) { "a" }
        cache.getOrPut(2) { "b" }
        cache.getOrPut(1) { "x" }
        cache.getOrPut(3) { "c" }

        assertThat(cache.size).isEqualTo(2)
        assertThat(cache.getOrPut(1) { "new" }).isEqualTo("a")
        assertThat(cache.getOrPut(2) { "new" }).isEqualTo("new")
    }
}
