package com.github.krpkbhdn.ccmanager.ui.extensions.skills

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.extension.ExtensionScope
import com.github.krpkbhdn.ccmanager.extension.skill.Skill
import com.github.krpkbhdn.ccmanager.extension.skill.SkillService
import com.github.krpkbhdn.ccmanager.notification.NotificationKind
import com.github.krpkbhdn.ccmanager.ui.extensions.MutationRunner
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.ExtensionFailure
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.UNEXPECTED_ERROR
import com.github.krpkbhdn.ccmanager.ui.extensions.plugins.failureOf
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import java.nio.file.Path
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface SkillText {
    data object Loading : SkillText
    data class Loaded(val content: String) : SkillText
    data class Failed(val message: String) : SkillText
}

/** What the skill screens can ask of the host: show a folder in the file manager or open it in the IDE. */
class SkillHost(val openFolder: (Path) -> Unit, val openIde: (Path) -> Unit, val ideAvailable: () -> Boolean)

const val NEW_SKILL_KEY = "skill:new"

/** Skills of one project (or of the user when [projectPath] is null); files are read and written one operation at a time. */
class SkillsState(
    private val service: SkillService,
    private val projectPath: Path?,
    private val scope: CoroutineScope,
    private val confirm: (ConfirmRequest) -> Unit,
    private val notify: (NotificationKind, String, String) -> Unit,
    val host: SkillHost,
) {
    private val mutations = MutationRunner(scope)
    var skills by mutableStateOf<List<Skill>>(emptyList())
        private set
    var loaded by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var loadFailure by mutableStateOf<ExtensionFailure?>(null)
    var selected by mutableStateOf<String?>(null)
    var creating by mutableStateOf(false)
    var query by mutableStateOf("")
    val texts = mutableStateMapOf<String, SkillText>()
    val job get() = mutations.job
    var failure: ExtensionFailure?
        get() = mutations.failure
        set(value) { mutations.failure = value }

    fun ensureLoaded() {
        if (!loaded && !loading) refresh()
    }

    fun refresh() {
        loading = true
        val seen = mutations.epoch
        scope.launch {
            try {
                val fresh = withContext(Dispatchers.IO) { service.list(projectPath) }
                if (seen == mutations.epoch) {
                    skills = fresh
                    texts.clear()
                }
                loadFailure = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                loadFailure = failureOf("Could not read skills", e)
            } finally {
                loading = false
                loaded = true
            }
        }
    }

    fun loadText(entry: SkillEntry) {
        if (texts[entry.key] is SkillText.Loaded) return
        texts[entry.key] = SkillText.Loading
        scope.launch {
            texts[entry.key] = try {
                SkillText.Loaded(withContext(Dispatchers.IO) { service.read(entry.skill) })
            } catch (e: CancellationException) {
                throw e
            } catch (e: RuntimeException) {
                SkillText.Failed(e.message ?: UNEXPECTED_ERROR)
            }
        }
    }

    fun create(target: ExtensionScope, name: String, description: String) {
        mutations.run("Creating $name", NEW_SKILL_KEY, { service.create(target, projectPath, name, description) to service.list(projectPath) }) { (created, all) ->
            skills = all
            creating = false
            selected = created.file.toString()
            notify(NotificationKind.FINISHED, "Created $name", "Edit its SKILL.md to tell Claude what it does.")
        }
    }

    fun requestDelete(entry: SkillEntry) {
        val name = entry.skill.name
        confirm(ConfirmRequest("Delete $name?", "Deletes the whole folder of the skill, including any files next to its SKILL.md. This cannot be undone.", "Delete") {
            mutations.run("Deleting $name", entry.key, { service.delete(entry.skill); service.list(projectPath) }) {
                skills = it
                texts.remove(entry.key)
                if (selected == entry.key) selected = null
            }
        })
    }
}
