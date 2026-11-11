package com.github.krpkbhdn.ccmanager.ui.presets

import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.ui.FakePlugins
import com.github.krpkbhdn.ccmanager.ui.FakeSkills
import com.github.krpkbhdn.ccmanager.ui.await
import com.github.krpkbhdn.ccmanager.ui.previewPresetService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PresetsStateTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val errors = mutableListOf<String>()
    private var confirmation: ConfirmRequest? = null
    private val service = previewPresetService()
    private val state = PresetsState(service, FakePlugins(), FakeSkills(), scope, { confirmation = it }, { errors += it })

    @BeforeEach
    fun start() {
        state.start()
        await { state.selected != null }
    }

    @AfterEach
    fun stop() {
        state.dispose()
        scope.cancel()
    }

    private fun storedComponents(id: Long) = service.get(id).orElseThrow().components()

    @Test
    fun `the bundled general preset is listed first and selected`() {
        assertThat(state.selected?.name()).isEqualTo("General")
    }

    @Test
    fun `new preset gets a free name and is selected`() {
        state.newPreset()
        await { state.selected?.name() == "New preset" }
        state.newPreset()
        await { state.selected?.name() == "New preset 2" }
    }

    @Test
    fun `adding plugins adds the missing marketplaces of the known ones first`() {
        val id = service.create("Mine", "").id()
        await { state.find(id) != null }
        state.open(ComponentEditor.PLUGINS, id)
        await { state.pluginChoices != null }
        val errors = mutableListOf<String>()

        state.addPlugins(id, listOf("context7@claude-plugins-official", "superpowers@superpowers-marketplace"), errors::add)
        await { state.dialog == null }

        assertThat(errors).isEmpty()
        assertThat(storedComponents(id).map { it.kind() to it.name() }).containsExactly(
            PresetComponentKind.MARKETPLACE to "claude-plugins-official", PresetComponentKind.MARKETPLACE to "superpowers-marketplace",
            PresetComponentKind.PLUGIN to "context7@claude-plugins-official", PresetComponentKind.PLUGIN to "superpowers@superpowers-marketplace",
        )
        assertThat(storedComponents(id).first().payload().text("source")).isEqualTo("anthropics/claude-plugins-official")
    }

    @Test
    fun `a rejected save keeps the dialog open and reports the message`() {
        val id = state.selected!!.id()
        state.open(ComponentEditor.FILE, id)
        val reported = mutableListOf<String>()

        state.save(state.dialog!!, PresetComponentKind.FILE, "../x", PresetPayloads.file("../x", "c", emptyList()), reported::add)
        await { reported.isNotEmpty() }

        assertThat(reported.single()).contains("inside the project")
        assertThat(state.dialog).isNotNull()
    }

    @Test
    fun `removing a component asks first and then saves at once`() {
        val preset = state.selected!!
        val first = preset.components().first()

        state.requestRemove(first)
        assertThat(storedComponents(preset.id())).hasSameSizeAs(preset.components())
        confirmation!!.onConfirm()
        await { storedComponents(preset.id()).none { it.id() == first.id() } }
    }

    @Test
    fun `search narrows the visible presets`() {
        service.create("Spring service", "")
        await { state.presets.size == 2 }

        state.query.edit { replace(0, length, "spring") }

        assertThat(state.visible.map { it.name() }).containsExactly("Spring service")
    }
}
