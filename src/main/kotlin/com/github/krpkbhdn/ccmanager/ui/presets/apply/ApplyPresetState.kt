package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.preset.apply.PlannedChange
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyResult
import com.github.krpkbhdn.ccmanager.preset.apply.PresetApplyService
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.presets.searchPresets
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The Apply preset dialog: choose a preset, review what it would change in the project, apply the checked changes, read the outcomes.
 * Planning and applying do file and CLI work, so they run off the UI thread; a project chosen first decides the folder.
 */
class ApplyPresetState(
    private val service: PresetApplyService,
    private val applications: PresetApplicationService,
    private val presets: () -> List<ProjectPreset>,
    private val projectOf: (Long) -> Project?,
    private val scope: CoroutineScope,
    private val reportError: (String) -> Unit,
) {
    /** True while the user still has to pick the project this opened without. */
    var pickingProject by mutableStateOf(false)
        private set
    var project by mutableStateOf<Project?>(null)
        private set
    var step by mutableStateOf(ApplyStep.CHOOSE)
        private set
    var presetId by mutableStateOf<Long?>(null)
        private set
    var applied by mutableStateOf<Map<Long, Int>>(emptyMap())
        private set
    /** Null while the plan is being worked out. */
    var plan by mutableStateOf<List<PlannedChange>?>(null)
        private set
    var selection by mutableStateOf<Set<Long>>(emptySet())
        private set
    var expanded by mutableStateOf<Set<Long>>(emptySet())
        private set
    var running by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var result by mutableStateOf<PresetApplyResult?>(null)
        private set
    val query = TextFieldState()

    private var pendingPresetId: Long? = null
    private var generation = 0

    val isOpen: Boolean get() = pickingProject || project != null

    val preset: ProjectPreset? get() = presetId?.let { id -> presets().firstOrNull { it.id() == id } }

    val visiblePresets: List<ProjectPreset> get() = searchPresets(presets(), query.text.toString())

    /** Opens for [projectId], or first asks for a project when null; with a [presetId] it starts at the review. */
    fun open(projectId: Long?, presetId: Long? = null) {
        if (isOpen) return
        reset()
        val known = projectId?.let(projectOf)
        if (known == null) {
            pendingPresetId = presetId
            pickingProject = true
        } else {
            begin(known, presetId)
        }
    }

    fun chooseProject(chosen: Project) {
        pickingProject = false
        begin(chosen, pendingPresetId)
    }

    fun statusOf(preset: ProjectPreset): PresetStatus? = presetStatus(preset, applied[preset.id()])

    fun select(id: Long) {
        presetId = id
    }

    fun review() {
        val id = presetId ?: return
        val folder = project?.path ?: return
        val mine = ++generation
        step = ApplyStep.REVIEW
        plan = null
        selection = emptySet()
        expanded = emptySet()
        error = null
        scope.launch {
            runGuarded({ if (mine == generation) { error = it; plan = emptyList() } }) {
                val planned = io { service.plan(id, folder) }
                if (mine == generation) {
                    plan = planned
                    selection = defaultSelection(planned)
                }
            }
        }
    }

    fun back() {
        if (running || step != ApplyStep.REVIEW) return
        generation++
        step = ApplyStep.CHOOSE
        error = null
    }

    fun toggle(change: PlannedChange) {
        if (!isSelectable(change)) return
        val id = change.componentId()
        selection = if (id in selection) selection - id else selection + id
    }

    fun toggleExpanded(id: Long) {
        expanded = if (id in expanded) expanded - id else expanded + id
    }

    fun apply() {
        val shown = project ?: return
        val id = presetId ?: return
        if (running || !canApply(plan, selection)) return
        val chosen = selection
        val mine = generation
        running = true
        error = null
        scope.launch {
            try {
                runGuarded({ error = it }) {
                    val outcome = io { service.apply(shown.id, id, shown.path, chosen) }
                    if (mine != generation) return@runGuarded
                    result = outcome
                    step = ApplyStep.RESULT
                    applied = io { applications.applications(shown.id) }
                }
            } finally {
                running = false
            }
        }
    }

    fun close() {
        generation++
        reset()
    }

    private fun begin(target: Project, startAt: Long?) {
        project = target
        presetId = startAt ?: presets().firstOrNull()?.id()
        scope.launch { runGuarded(reportError) { applied = io { applications.applications(target.id) } } }
        if (startAt != null) review()
    }

    private fun reset() {
        pickingProject = false
        pendingPresetId = null
        project = null
        step = ApplyStep.CHOOSE
        presetId = null
        applied = emptyMap()
        plan = null
        selection = emptySet()
        expanded = emptySet()
        running = false
        error = null
        result = null
        query.edit { replace(0, length, "") }
    }
}
