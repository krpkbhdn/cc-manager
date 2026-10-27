package com.github.krpkbhdn.ccmanager.ui.prompts

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplate
import com.github.krpkbhdn.ccmanager.prompt.PromptTemplateService
import com.github.krpkbhdn.ccmanager.ui.state.ConfirmRequest
import com.github.krpkbhdn.ccmanager.ui.state.Refresher
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Input of the create/edit dialog; [existing] is null for a new template. */
class PromptDialogState(val existing: PromptTemplate?) {
    val name = TextFieldState(existing?.name().orEmpty())
    val text = TextFieldState(existing?.text().orEmpty())
    var projectId by mutableStateOf(existing?.projectId())
    var teamId by mutableStateOf(existing?.defaultTeamId())
    val hasInput: Boolean get() = name.text.isNotEmpty() || text.text.isNotEmpty()
}

/** Prompt templates of the Templates screen; every backend change callback reloads the list, writes run off the UI thread. */
class PromptsState(
    private val service: PromptTemplateService,
    private val scope: CoroutineScope,
    private val requestConfirm: (ConfirmRequest) -> Unit,
    private val reportError: (String) -> Unit,
    private val otherSources: List<(Runnable) -> Runnable> = emptyList(),
) {
    var templates by mutableStateOf<List<PromptTemplate>>(emptyList())
        private set
    var dialog by mutableStateOf<PromptDialogState?>(null)
        private set

    private val unsubscribers = mutableListOf<Runnable?>()
    private val reload = Refresher(scope) { runGuarded(reportError) { templates = io { service.list() } } }

    /** [otherSources] (projects, teams) are the lists a template points at, so their changes reload the templates too. */
    fun start() {
        (listOf<(Runnable) -> Runnable>(service::subscribe) + otherSources).forEach { subscribe -> unsubscribers += subscribe(Runnable { reload.request() }) }
        reload.request()
    }

    fun dispose() {
        unsubscribers.forEach { it?.run() }
        unsubscribers.clear()
    }

    fun find(id: Long): PromptTemplate? = templates.firstOrNull { it.id == id }

    /** Global templates plus the ones bound to [projectId]. */
    fun forProject(projectId: Long): List<PromptTemplate> = templates.filter { it.projectId == null || it.projectId == projectId }

    fun openDialog(existing: PromptTemplate? = null) {
        dialog = PromptDialogState(existing)
    }

    fun closeDialog() {
        dialog = null
    }

    fun save(form: PromptDialogState, onError: (String) -> Unit) {
        scope.launch {
            runGuarded(onError) {
                val name = form.name.text.toString()
                val text = form.text.text.toString()
                io { form.existing?.let { service.update(it.id, name, text, form.projectId, form.teamId) } ?: service.create(name, text, form.projectId, form.teamId) }
                dialog = null
            }
        }
    }

    fun requestDelete(template: PromptTemplate) {
        requestConfirm(ConfirmRequest("Delete template", "Delete the template \"${template.name}\"?", "Delete") {
            scope.launch { runGuarded(reportError) { io { service.delete(template.id) } } }
        })
    }
}
