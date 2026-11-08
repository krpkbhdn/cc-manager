package com.github.krpkbhdn.ccmanager.ui.presets.apply

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.github.krpkbhdn.ccmanager.preset.PresetApplicationService
import com.github.krpkbhdn.ccmanager.preset.ProjectPreset
import com.github.krpkbhdn.ccmanager.project.Project
import com.github.krpkbhdn.ccmanager.ui.state.io
import com.github.krpkbhdn.ccmanager.ui.state.runGuarded
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private const val GENERAL_KEY = "general"

/** The question "Set up this project?"; [presetId] is the preset the dropdown currently names. */
class PresetOffer(val project: Project, presetId: Long) {
    var presetId by mutableStateOf(presetId)
}

/**
 * Offers a preset when a project without any applied preset is opened: once per run and project, never while another dialog is in the way
 * (then the next visit asks), and never again after "Not for this project".
 */
class PresetOfferState(
    private val applications: PresetApplicationService,
    private val presets: () -> List<ProjectPreset>,
    private val projectOf: (Long) -> Project?,
    private val scope: CoroutineScope,
    private val blocked: () -> Boolean,
    private val review: (projectId: Long, presetId: Long) -> Unit,
    private val reportError: (String) -> Unit,
) {
    var offer by mutableStateOf<PresetOffer?>(null)
        private set

    private val asked = mutableSetOf<Long>()

    fun offerFor(projectId: Long) {
        val project = projectOf(projectId)?.takeIf { !it.group }
        if (project == null || blocked() || projectId in asked) return
        scope.launch {
            runGuarded(reportError) {
                val wanted = io { applications.applications(projectId).isEmpty() && !applications.offerDeclined(projectId) }
                val all = presets()
                if (!wanted) {
                    asked += projectId
                } else if (!blocked() && offer == null && all.isNotEmpty()) {
                    asked += projectId
                    offer = PresetOffer(project, (all.firstOrNull { it.bundledKey() == GENERAL_KEY } ?: all.first()).id())
                }
            }
        }
    }

    fun choose(presetId: Long) {
        offer?.presetId = presetId
    }

    fun reviewAndApply() {
        val shown = offer ?: return
        offer = null
        review(shown.project.id, shown.presetId)
    }

    fun decline() {
        val shown = offer ?: return
        offer = null
        scope.launch { runGuarded(reportError) { io { applications.declineOffer(shown.project.id) } } }
    }

    fun later() {
        offer = null
    }
}
