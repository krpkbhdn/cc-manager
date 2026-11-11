package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PluginPickerTest {
    private fun plugin(name: String, market: String, description: String = "") = AvailablePlugin("$name@$market", name, description, market, 0)

    private val available = listOf(plugin("zeta", "b-market", "Last one"), plugin("alpha", "b-market"), plugin("code-review", "a-market", "Reviews code"))

    @Test
    fun `plugins are grouped by marketplace and sorted by name`() {
        val groups = pluginGroups(available, emptyList(), emptySet(), "")
        assertThat(groups.map { it.marketplace }).containsExactly("a-market", "b-market")
        assertThat(groups[1].plugins.map { it.name }).containsExactly("alpha", "zeta")
    }

    @Test
    fun `installed plugins the marketplaces no longer list still show up`() {
        val groups = pluginGroups(available, listOf("old@gone-market", "alpha@b-market"), emptySet(), "")
        assertThat(groups.map { it.marketplace }).contains("gone-market")
        assertThat(groups.flatMap { it.plugins }.count { it.id == "alpha@b-market" }).isEqualTo(1)
    }

    @Test
    fun `plugins already in the preset are marked`() {
        val groups = pluginGroups(available, emptyList(), setOf("alpha@b-market"), "")
        assertThat(groups.flatMap { it.plugins }.filter { it.added }.map { it.id }).containsExactly("alpha@b-market")
    }

    @Test
    fun `search matches name description and marketplace words`() {
        assertThat(pluginGroups(available, emptyList(), emptySet(), "reviews a-market").flatMap { it.plugins }.map { it.name }).containsExactly("code-review")
        assertThat(pluginGroups(available, emptyList(), emptySet(), "nothing")).isEmpty()
    }

    @Test
    fun `only the missing known marketplaces of the chosen plugins are added`() {
        val known = listOf(Marketplace("a-market", "github", "acme/a", ""), Marketplace("b-market", "git", "https://x/b.git", ""), Marketplace("c-market", "path", "/c", ""))
        val toAdd = marketplacesToAdd(listOf("code-review@a-market", "alpha@b-market", "zeta@b-market"), setOf("b-market"), known)
        assertThat(toAdd.map { it.name() }).containsExactly("a-market")
    }
}
