package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.preset.PresetComponent
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class PresetModelTest {
    private fun component(kind: PresetComponentKind, name: String = "x") = PresetComponent(0, kind, name, PresetPayloads.plugin())

    private fun preset(name: String, description: String = "", edited: Boolean = false, defaultChanged: Boolean = false, components: List<PresetComponent> = emptyList()) =
        ProjectPreset(1, name, description, null, edited, defaultChanged, 0, components)

    @Test
    fun `summary counts each kind with singular and plural`() {
        val components = List(4) { component(PresetComponentKind.INSTRUCTION, "i$it") } + List(2) { component(PresetComponentKind.SKILL, "s$it") } + component(PresetComponentKind.FILE)
        assertThat(summaryOf(components)).isEqualTo("4 instructions · 2 skills · 1 file")
    }

    @Test
    fun `summary leaves out marketplaces and names settings without a count`() {
        val components = listOf(component(PresetComponentKind.MARKETPLACE), component(PresetComponentKind.PLUGIN), component(PresetComponentKind.MCP_SERVER), component(PresetComponentKind.SETTINGS))
        assertThat(summaryOf(components)).isEqualTo("1 plugin · 1 MCP server · settings")
    }

    @Test
    fun `an empty preset says so`() {
        assertThat(summaryOf(emptyList())).isEqualTo("Empty")
    }

    @Test
    fun `plugins tab counts plugins only while it shows marketplaces too`() {
        val preset = preset("p", components = listOf(component(PresetComponentKind.MARKETPLACE), component(PresetComponentKind.PLUGIN), component(PresetComponentKind.PLUGIN, "y")))
        assertThat(PresetTab.PLUGINS.countIn(preset)).isEqualTo(2)
        assertThat(PresetTab.PLUGINS.componentsOf(preset)).hasSize(3)
        assertThat(PresetTab.SETTINGS.countIn(preset)).isZero()
    }

    @Test
    fun `search needs every word in the name or the description`() {
        val all = listOf(preset("Spring Boot service", "REST backend"), preset("Compose app", "desktop UI"), preset("Docs"))
        assertThat(searchPresets(all, "spring rest").map { it.name() }).containsExactly("Spring Boot service")
        assertThat(searchPresets(all, "  ")).hasSize(3)
        assertThat(searchPresets(all, "zzz")).isEmpty()
    }

    @Test
    fun `a row carries at most one chip and an update outranks modified`() {
        assertThat(chipOf(preset("a"))).isNull()
        assertThat(chipOf(preset("a", edited = true))).isEqualTo("Modified")
        assertThat(chipOf(preset("a", edited = true, defaultChanged = true))).isEqualTo("Update available")
    }

    @Test
    fun `unique name appends the first free number`() {
        assertThat(uniqueName("New preset", listOf("New preset", "new preset 2"), 60)).isEqualTo("New preset 3")
        assertThat(uniqueName("New preset", emptyList(), 60)).isEqualTo("New preset")
    }
}
