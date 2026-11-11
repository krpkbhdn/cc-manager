package com.github.krpkbhdn.ccmanager.ui.presets

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.extension.plugin.AvailablePlugin
import com.github.krpkbhdn.ccmanager.extension.plugin.Marketplace
import com.github.krpkbhdn.ccmanager.extension.plugin.PluginService
import com.github.krpkbhdn.ccmanager.extension.skill.Skill
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService
import com.github.krpkbhdn.ccmanager.preset.PresetComponent
import com.github.krpkbhdn.ccmanager.preset.PresetComponentKind
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.preset.ProjectPresetService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import tools.jackson.databind.JsonNode

private const val NEW_PRESET_NAME = "New preset"
private const val COPY_SUFFIX = " copy"

/** Which dialog edits a component; the plugin and folder ones add several at once. */
enum class ComponentEditor { PLUGINS, SKILL_ADD, SKILL_EDIT, INSTRUCTION, MCP, AGENT, FILE, SETTINGS }

/** The open dialog; [editing] is the component being changed, null when adding. */
class ComponentDialog(val editor: ComponentEditor, val presetId: Long, val editing: PresetComponent? = null)

/** What the plugin picker lists: the marketplaces' plugins, the installed ones and the marketplaces to copy sources from. */
class PluginChoices(val available: List<AvailablePlugin>,val installed: List<String>, val marketplaces: List<Marketplace>)

/**
 * Project presets the Settings page lists and the components of the selected one. Every change is saved at once; writes run
 * off the UI thread, failures go to [report] or to the dialog's own error line. Other screens read [presets] and [find].
 */
class PresetsState(
    private val service: ProjectPresetService,
    private val plugins: PluginService,
    private val skills: SkillService,
    private val scope: CoroutineScope,
    private val confirm: (ConfirmRequest) -> Unit,
    private val report: (String) -> Unit,
) {
    var presets by mutableStateOf<List<ProjectPreset>>(emptyList())
        private set
    var tab by mutableStateOf(PresetTab.INSTRUCTIONS)
    var dialog by mutableStateOf<ComponentDialog?>(null)
        private set
    var pluginChoices by mutableStateOf<PluginChoices?>(null)
        private set
    var skillChoices by mutableStateOf<List<Skill>?>(null)
        private set
    val query = TextFieldState()

    private var selectedId by mutableStateOf<Long?>(null)
    private var unsubscribe: Runnable? = null
    @Volatile
    private var creating = false

    val selected: ProjectPreset? get() = presets.firstOrNull { it.id() == selectedId } ?: presets.firstOrNull()

    val visible: List<ProjectPreset> get() = searchPresets(presets, query.text.toString())

    fun start() {
        unsubscribe = service.subscribe { scope.launch { reload() } }
        scope.launch { reload() }
    }

    fun dispose() = unsubscribe?.run()

    fun find(id: Long): ProjectPreset? = presets.firstOrNull { it.id() == id }

    fun select(preset: ProjectPreset) {
        selectedId = preset.id()
    }

    fun newPreset() {
        if (creating) return
        creating = true
        run {
            val created = try {
                withContext(Dispatchers.IO) { service.create(uniqueName(NEW_PRESET_NAME, presets.map { it.name() }, ProjectPresetService.MAX_NAME), "") }
            } finally {
                creating = false
            }
            selectedId = created.id()
            query.edit { replace(0, length, "") }
        }
    }

    fun saveDetails(preset: ProjectPreset, name: String, description: String) = run {
        withContext(Dispatchers.IO) { service.update(preset.id(), name, description) }
    }

    fun duplicate(preset: ProjectPreset) = run {
        selectedId = withContext(Dispatchers.IO) { service.duplicate(preset.id(), uniqueName(preset.name() + COPY_SUFFIX, presets.map { it.name() }, ProjectPresetService.MAX_NAME)) }.id()
    }

    fun requestDelete(preset: ProjectPreset) {
        val restorable = if (preset.bundledKey() != null) " You can bring it back with \"Restore bundled presets\"." else ""
        confirm(ConfirmRequest("Delete preset", "Delete \"${preset.name()}\"? Projects that applied it keep their setup.$restorable", "Delete") {
            run { withContext(Dispatchers.IO) { service.delete(preset.id()) } }
        })
    }

    fun requestRestoreDefault(preset: ProjectPreset) {
        val key = preset.bundledKey() ?: return
        confirm(ConfirmRequest("Restore default", "Replace \"${preset.name()}\" with the version shipped with the app? Your changes to it are lost.", "Restore") {
            run { withContext(Dispatchers.IO) { service.restore(key) } }
        })
    }

    fun requestRestoreBundled() {
        confirm(ConfirmRequest("Restore bundled presets", "Bring back deleted bundled presets and reset the edited ones to the shipped version? Presets you created stay as they are.", "Restore") {
            run { withContext(Dispatchers.IO) { service.restoreAll() } }
        })
    }

    fun requestRemove(component: PresetComponent) {
        confirm(ConfirmRequest("Remove from preset", "Remove \"${lineOf(component).title}\" from the preset?", "Remove") {
            run { withContext(Dispatchers.IO) { service.removeComponent(component.id()) } }
        })
    }

    fun move(component: PresetComponent, up: Boolean) = run { withContext(Dispatchers.IO) { service.moveComponent(component.id(), up) } }

    fun open(editor: ComponentEditor, presetId: Long, editing: PresetComponent? = null) {
        dialog = ComponentDialog(editor, presetId, editing)
        when (editor) {
            ComponentEditor.PLUGINS -> loadPluginChoices()
            ComponentEditor.SKILL_ADD -> loadSkillChoices()
            else -> Unit
        }
    }

    fun closeDialog() {
        dialog = null
        pluginChoices = null
        skillChoices = null
    }

    /** Adds or changes one component; the dialog closes on success and [onError] gets the message otherwise. */
    fun save(open: ComponentDialog, kind: PresetComponentKind, name: String, payload: JsonNode, onError: (String) -> Unit) = inDialog(onError) {
        val editing = open.editing
        if (editing == null) service.addComponent(open.presetId, kind, name, payload) else service.updateComponent(editing.id(), name, payload)
    }

    /** Adds the chosen plugins, first the marketplaces they come from when the preset lacks them. */
    fun addPlugins(presetId: Long, pluginIds: List<String>, onError: (String) -> Unit) = inDialog(onError) {
        val preset = service.get(presetId).orElseThrow { IllegalArgumentException("The preset no longer exists.") }
        val known = pluginChoices?.marketplaces.orEmpty()
        val present = preset.components().filter { it.kind() == PresetComponentKind.MARKETPLACE }.map { it.name() }.toSet()
        marketplacesToAdd(pluginIds, present, known).forEach { service.addComponent(presetId, PresetComponentKind.MARKETPLACE, it.name(), PresetPayloads.marketplace(it.reference())) }
        val have = preset.components().filter { it.kind() == PresetComponentKind.PLUGIN }.map { it.name() }.toSet()
        pluginIds.filter { it !in have }.forEach { service.addComponent(presetId, PresetComponentKind.PLUGIN, it, PresetPayloads.plugin()) }
    }

    /** Copies the installed user skills with their text files into the preset. */
    fun addInstalledSkills(presetId: Long, chosen: List<Skill>, onError: (String) -> Unit) = inDialog(onError) {
        chosen.forEach { skill ->
            val copy = readSkillFolder(skill.directory())
            check(copy.problem == null) { "${skill.name()}: ${copy.problem}" }
            service.addComponent(presetId, PresetComponentKind.SKILL, skill.name(), PresetPayloads.skill(copy.files))
        }
    }

    /** Reads a skill folder off the UI thread for the import preview. */
    fun readFolder(directory: Path, onRead: (SkillImport) -> Unit) {
        scope.launch {
            val read = withContext(Dispatchers.IO) { readSkillFolder(directory) }
            onRead(read)
        }
    }

    private fun loadPluginChoices() {
        scope.launch {
            try {
                pluginChoices = withContext(Dispatchers.IO) { PluginChoices(plugins.available(), plugins.installed().map { it.id() }.distinct(), plugins.marketplaces()) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                pluginChoices = PluginChoices(emptyList(), emptyList(), emptyList())
                report(e.message ?: "Could not list the plugins")
            }
        }
    }

    private fun loadSkillChoices() {
        scope.launch {
            try {
                skillChoices = withContext(Dispatchers.IO) { skills.list(null).filter { !it.readOnly() } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                skillChoices = emptyList()
                report(e.message ?: "Could not list the skills")
            }
        }
    }

    private fun inDialog(onError: (String) -> Unit, block: () -> Unit) {
        scope.launch {
            try {
                withContext(Dispatchers.IO) { block() }
                closeDialog()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                onError(e.message ?: "Could not save")
            }
        }
    }

    private suspend fun reload() {
        try {
            presets = withContext(Dispatchers.IO) { service.list() }
        } catch (e: CancellationException) {
            throw e
        } catch (e: RuntimeException) {
            report(e.message ?: "Could not load the presets")
        }
    }

    private fun run(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                report(e.message ?: "Could not change the preset")
            }
        }
    }
}
