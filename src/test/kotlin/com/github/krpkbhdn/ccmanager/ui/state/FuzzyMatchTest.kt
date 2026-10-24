package com.github.krpkbhdn.ccmanager.ui.state

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class FuzzyMatchTest {
    @Test
    fun `empty query matches everything with a neutral score`() {
        assertThat(fuzzyMatch("", "Add project")).isEqualTo(FuzzyResult(0, emptyList()))
    }

    @Test
    fun `characters must appear in order`() {
        assertThat(fuzzyMatch("ap", "Add project")).isNotNull
        assertThat(fuzzyMatch("pa", "Add")).isNull()
    }

    @Test
    fun `matching ignores case and spaces in the query`() {
        assertThat(fuzzyMatch("OPEN ide", "Open in IntelliJ IDEA")).isNotNull
    }

    @Test
    fun `reports the matched positions`() {
        assertThat(fuzzyMatch("dm", "docs-manager")!!.positions).containsExactly(0, 5)
    }

    @Test
    fun `word starts and consecutive runs beat scattered hits`() {
        val scattered = fuzzyMatch("man", "make-and-nothing")!!.score
        val prefix = fuzzyMatch("man", "manager")!!.score
        assertThat(prefix).isGreaterThan(scattered)
    }

    @Test
    fun `filter orders better matches first and drops misses`() {
        val items = listOf("Toggle sidebar", "Add project", "Go to overview", "Launch Claude")
        assertThat(fuzzyFilter("pro", items) { it }).containsExactly("Add project")
        assertThat(fuzzyFilter("la", items) { it }.first()).isEqualTo("Launch Claude")
    }

    @Test
    fun `filter keeps the original order for equal scores`() {
        val items = listOf("alpha", "alpha")
        assertThat(fuzzyFilter("a", items.mapIndexed { i, s -> "$s$i" }) { it.dropLast(1) }).containsExactly("alpha0", "alpha1")
    }
}
